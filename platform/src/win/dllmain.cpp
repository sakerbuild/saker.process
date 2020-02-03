/*
 * Copyright (C) 2020 Bence Sipka
 *
 * This program is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
#include <jni.h>
#include <Windows.h>
#include <saker_process_platform_NativeProcess.h>
#include <saker_process_platform_win32_Win32NativeProcess.h>
#include <string>

static std::wstring Java_To_WStr(JNIEnv *env, jstring string) {
	if (string == NULL) {
		return L"";
	}
    std::wstring value;

    const jchar *raw = env->GetStringChars(string, 0);
    jsize len = env->GetStringLength(string);

    value.assign(raw, raw + len);

    env->ReleaseStringChars(string, raw);

    return value;
}

static void javaException(JNIEnv* env, const char* type, const char* message) {
	jclass jc = env->FindClass(type);
	env->ThrowNew(jc, message);
}
static void failureType(JNIEnv* env, const char* exceptiontype, const char* function, DWORD error){
	char msg[256];
	sprintf_s(msg, "%s: %d", function, error);
	javaException(env, exceptiontype, msg);
}
static void failure(JNIEnv* env, const char* function, DWORD error){
	failureType(env, "java/io/IOException", function, error);
}
static void interruptException(JNIEnv* env, const char* message) {
	javaException(env, "java/lang/InterruptedException", message);
}

// from https://blogs.msdn.microsoft.com/twistylittlepassagesallalike/2011/04/23/everyone-quotes-command-line-arguments-the-wrong-way/
/*++
Routine Description:
    
    This routine appends the given argument to a command line such
    that CommandLineToArgvW will return the argument string unchanged.
    Arguments in a command line should be separated by spaces; this
    function does not add these spaces.
    
Arguments:
    
    Argument - Supplies the argument to encode.

    CommandLine - Supplies the command line to which we append the encoded argument string.

    Force - Supplies an indication of whether we should quote
            the argument even if it does not contain any characters that would
            ordinarily require quoting.
    
Return Value:
    
    None.
    
Environment:
    
    Arbitrary.
--*/
static void ArgvQuote (
    const std::wstring& Argument,
    std::wstring& CommandLine,
    bool Force
) {
    //
    // Unless we're told otherwise, don't quote unless we actually
    // need to do so --- hopefully avoid problems if programs won't
    // parse quotes properly
    //
    
    if (Force == false &&
        Argument.empty () == false &&
        Argument.find_first_of (L" \t\n\v\"") == Argument.npos) {
        CommandLine.append (Argument);
    } else {
        CommandLine.push_back (L'"');
        
        for (auto It = Argument.begin () ; ; ++It) {
            unsigned NumberBackslashes = 0;
        
            while (It != Argument.end () && *It == L'\\') {
                ++It;
                ++NumberBackslashes;
            }
        
            if (It == Argument.end ()) {
                
                //
                // Escape all backslashes, but let the terminating
                // double quotation mark we add below be interpreted
                // as a metacharacter.
                //
                
                CommandLine.append (NumberBackslashes * 2, L'\\');
                break;
            } else if (*It == L'"') {

                //
                // Escape all backslashes and the following
                // double quotation mark.
                //
                
                CommandLine.append (NumberBackslashes * 2 + 1, L'\\');
                CommandLine.push_back (*It);
            } else {
                
                //
                // Backslashes aren't special here.
                //
                
                CommandLine.append (NumberBackslashes, L'\\');
                CommandLine.push_back (*It);
            }
        }
    
        CommandLine.push_back (L'"');
    }
}

class NativeProcess {
private:
public:
	const PROCESS_INFORMATION procInfo;
	const STARTUPINFOW startupInfo;
	HANDLE stdOutPipeIn;
	HANDLE stdOutPipeOut;
	HANDLE stdErrPipeIn;
	HANDLE stdErrPipeOut;
	HANDLE interruptEvent;
	jint flags;
	
	jobject standardOutputConsumer; 
	jobject standardErrorConsumer;
	
	HANDLE stdOutFile = INVALID_HANDLE_VALUE;
	HANDLE stdErrFile = INVALID_HANDLE_VALUE;
	
	NativeProcess(
			const PROCESS_INFORMATION& pi, 
			const STARTUPINFOW& si, 
			HANDLE stdoutpipein, 
			HANDLE stdoutpipeout,
			HANDLE stderrpipein, 
			HANDLE stderrpipeout,
			jint flags,
			HANDLE interruptevent,
			jobject standardOutputConsumer,
			jobject standardErrorConsumer)
		: 
			procInfo(pi), 
			startupInfo(si),
			stdOutPipeIn(stdoutpipein),
			stdOutPipeOut(stdoutpipeout),
			stdErrPipeIn(stderrpipein),
			stdErrPipeOut(stderrpipeout),
			interruptEvent(interruptevent),
			flags(flags),
			standardOutputConsumer(standardOutputConsumer),
			standardErrorConsumer(standardErrorConsumer) {
	}
	
	bool hasStdOut() const {
		return stdOutPipeIn != INVALID_HANDLE_VALUE;
	}
	bool hasStdErr() const {
		return stdErrPipeIn != INVALID_HANDLE_VALUE;
	}
	bool hasStdErrDifferentFromStdOut() const {
		return stdErrPipeIn != INVALID_HANDLE_VALUE && stdErrPipeIn != stdOutPipeIn;
	}
};
struct HandleCloser {
	HANDLE handle;
	
	HandleCloser(HANDLE handle = INVALID_HANDLE_VALUE) : handle(handle) {
	}
	
	HandleCloser& operator=(HANDLE handle) {
		if(this->handle != INVALID_HANDLE_VALUE) {
			CloseHandle(this->handle);
		}
		this->handle = handle;
		return *this;
	}
	
	~HandleCloser() {
		if(handle != INVALID_HANDLE_VALUE) {
			CloseHandle(handle);
		}
	}
};

#define PIPE_NAME_PREFIX "\\\\.\\pipe\\"

static HANDLE createNamedPipeWithName(const char* pipename) {
	return CreateNamedPipe(
		pipename,
		PIPE_ACCESS_INBOUND | FILE_FLAG_FIRST_PIPE_INSTANCE | FILE_FLAG_OVERLAPPED,
		PIPE_TYPE_BYTE | PIPE_READMODE_BYTE | PIPE_WAIT | PIPE_REJECT_REMOTE_CLIENTS,
		1,
		16 * 1024,
		16 * 1024,
		INFINITE,
		NULL
	);
}
static HANDLE openPipeWrite(const char* pipename) {
	// To inherit the handles the bInheritHandle flag so pipe handles are inherited.
	SECURITY_ATTRIBUTES secattrs_inherit_handle = {};
	secattrs_inherit_handle.nLength = sizeof(SECURITY_ATTRIBUTES);
	secattrs_inherit_handle.bInheritHandle = TRUE;
	
	return CreateFile(
		pipename,
		GENERIC_WRITE,
		0,
		&secattrs_inherit_handle,
		OPEN_EXISTING,
		0,
		NULL
	);
}

#define FLAG_IS_MERGE_STDERR(flags) ((flags & Java_const_saker_process_platform_NativeProcess_FLAG_MERGE_STDERR) == Java_const_saker_process_platform_NativeProcess_FLAG_MERGE_STDERR)

JNIEXPORT jlong JNICALL Java_saker_process_platform_win32_Win32NativeProcess_native_1startProcess(
	JNIEnv* env, 
	jclass clazz, 
	jstring exe, 
	jobjectArray commands, 
	jstring workingdirectory,
	jint flags, 
	jstring pipeid,
	jlong interrupteventptr,
	jstring envstr,
	jobject standardOutputConsumer, 
	jobject standardErrorConsumer, 
	jstring stdoutfilepath, 
	jstring stderrfilepath
) {
	PROCESS_INFORMATION pi;
	ZeroMemory(&pi, sizeof(pi));
	
	STARTUPINFOW si;
	ZeroMemory(&si, sizeof(si));
	si.cb = sizeof(si);
	
	char pipename[sizeof(PIPE_NAME_PREFIX) + 3 + 64];

	memcpy(pipename, PIPE_NAME_PREFIX, sizeof(PIPE_NAME_PREFIX));
	jsize pipeidlen = env->GetStringLength(pipeid);
	if (pipeidlen >= 64) {
		failureType(env, "java/lang/IllegalArgumentException", "Invalid process pipe length", pipeidlen);
		return 0;
	}
	const jchar* pipeidchars = env->GetStringChars(pipeid, NULL);
	char* pipenameptr = pipename + (sizeof(PIPE_NAME_PREFIX) - 1);
	char* errpipenameid = pipenameptr;
	*pipenameptr++ = 's';
	*pipenameptr++ = 't';
	*pipenameptr++ = 'd';
	for(jsize i = 0; i < pipeidlen; ++i){
		*pipenameptr++ = (char) pipeidchars[i];
	}
	//terminating null
	*pipenameptr++ = 0;
	env->ReleaseStringChars(pipeid, pipeidchars);
	
	HandleCloser stdoutnamedpipe;
	HandleCloser stdoutwritepipe;
	HandleCloser stderrnamedpipe;
	HandleCloser stderrwritepipe;
	
	HandleCloser stdoutfilehandlecloser;
	HandleCloser stderrfilehandlecloser;
	
	HANDLE stdoutpipein = INVALID_HANDLE_VALUE;
	HANDLE stdoutpipeout = INVALID_HANDLE_VALUE;
	HANDLE stderrpipein = INVALID_HANDLE_VALUE;
	HANDLE stderrpipeout = INVALID_HANDLE_VALUE;
	
	if (standardOutputConsumer != NULL) {
		stdoutnamedpipe = createNamedPipeWithName(pipename);
		if(stdoutnamedpipe.handle == INVALID_HANDLE_VALUE){
			failure(env, "CreateNamedPipe", GetLastError());
			return 0;
		}
		
		stdoutwritepipe = openPipeWrite(pipename);
		if (stdoutwritepipe.handle == INVALID_HANDLE_VALUE){
			failure(env, "CreateFile", GetLastError());
			return 0;
		}
		stdoutpipein = stdoutnamedpipe.handle;
		stdoutpipeout = stdoutwritepipe.handle;
		si.hStdOutput = stdoutpipeout;
	} else if (stdoutfilepath != NULL) {
		std::wstring fname = Java_To_WStr(env, stdoutfilepath);
	
		SECURITY_ATTRIBUTES secattrs_inherit_handle = {};
		secattrs_inherit_handle.nLength = sizeof(SECURITY_ATTRIBUTES);
		secattrs_inherit_handle.bInheritHandle = TRUE;
		
	    HANDLE h = CreateFileW(fname.c_str(),
	        GENERIC_WRITE,
	        FILE_SHARE_READ,
	        &secattrs_inherit_handle,
	        OPEN_ALWAYS,
	        FILE_ATTRIBUTE_NORMAL,
	        NULL 
        );
        if (h == INVALID_HANDLE_VALUE) {
        	failure(env, "stdout CreateFile", GetLastError());
			return 0;
        }
        stdoutfilehandlecloser = h;
        //truncate the file if it was longer when opened
        SetEndOfFile(h);
        si.hStdOutput = h;
	}
	
	if (FLAG_IS_MERGE_STDERR(flags)) {
		if (si.hStdOutput == INVALID_HANDLE_VALUE) {
			failureType(env, "java/lang/IllegalArgumentException", "Cannot merge standard error to null standard output.", NULL);
			return 0;
		}
		si.hStdError = si.hStdOutput;
	} else if(standardErrorConsumer != NULL) {
		//don't merge standard error
		*errpipenameid++ = 'e';
		*errpipenameid++ = 'r';
		*errpipenameid++ = 'r';
		
		stderrnamedpipe = createNamedPipeWithName(pipename);
		if(stderrnamedpipe.handle == INVALID_HANDLE_VALUE){
			failure(env, "CreateNamedPipe", GetLastError());
			return 0;
		}
		
		stderrwritepipe = openPipeWrite(pipename);
		if (stderrwritepipe.handle == INVALID_HANDLE_VALUE){
			failure(env, "CreateFile", GetLastError());
			return 0;
		}
		stderrpipein = stderrnamedpipe.handle;
		stderrpipeout = stderrwritepipe.handle;
		si.hStdError = stderrpipeout;
	} else if (stderrfilepath != NULL) {
		std::wstring fname = Java_To_WStr(env, stderrfilepath);

		SECURITY_ATTRIBUTES secattrs_inherit_handle = {};
		secattrs_inherit_handle.nLength = sizeof(SECURITY_ATTRIBUTES);
		secattrs_inherit_handle.bInheritHandle = TRUE;
		
	    HANDLE h = CreateFileW(fname.c_str(),
	        GENERIC_WRITE,
	        FILE_SHARE_READ,
	        &secattrs_inherit_handle,
	        OPEN_ALWAYS,
	        FILE_ATTRIBUTE_NORMAL,
	        NULL 
        );
        if (h == INVALID_HANDLE_VALUE) {
        	failure(env, "stderr CreateFile", GetLastError());
			return 0;
        }
        stderrfilehandlecloser = h;
        //truncate the file if it was longer when opened
        SetEndOfFile(h);
        si.hStdError = h;
	}
	
	si.hStdInput = INVALID_HANDLE_VALUE;
	si.dwFlags |= STARTF_USESTDHANDLES;

	std::wstring modname = Java_To_WStr(env, exe);
	std::wstring workdir = Java_To_WStr(env, workingdirectory);
	std::wstring environmentwstr = Java_To_WStr(env, envstr);
 	jsize cmdlen = commands == NULL ? 0 : env->GetArrayLength(commands);
 	std::wstring cmdstr;
 	
 	//include the executable as the zeroth argument
 	if (exe != NULL) {
 		ArgvQuote(modname, cmdstr, false);
 	}
 	for(jsize i = 0; i < cmdlen; ++i){
 		jstring c = static_cast<jstring>(env->GetObjectArrayElement(commands, i));
 		std::wstring cstr = Java_To_WStr(env, c);
 		if(cmdstr.length() > 0){
			cmdstr.push_back(L' ');
		}
 		ArgvQuote(cstr, cmdstr, false);
 		env->DeleteLocalRef(c);
 	}
	
	wchar_t* cmd = new wchar_t[cmdstr.length() + 1];
	wcscpy(cmd, cmdstr.c_str());
	cmd[cmdstr.length()] = 0;
	
	LPCWSTR modnamestr = exe == NULL ? NULL : modname.c_str();
	LPCWSTR workingdirstr = workingdirectory == NULL ? NULL : workdir.c_str();
	
	LPVOID envblock = envstr == NULL ? NULL : (void*) environmentwstr.c_str();
	
	jobject outputconsumerref = standardOutputConsumer == NULL ? NULL : env->NewGlobalRef(standardOutputConsumer);
	jobject errorconsumerref = standardErrorConsumer == NULL ? NULL : env->NewGlobalRef(standardErrorConsumer);
	//XXX error handle global reference creation

	//notes: the CREATE_NO_WINDOW flag increases startup time SIGNIFICANTLY. like + 15 ms or so for simple processes
	//       not specifying it creates a new console when used without one. e.g. in eclipse
	//       the DETACHED_PROCESS solves the console creation, and the startup time
	if (!CreateProcessW(
			modnamestr,			// exe path
			cmd,				// Command line
			NULL,       		// process security attributes
			NULL,       		// thread security attributes
			TRUE,      			// handle inheritance
			DETACHED_PROCESS | CREATE_UNICODE_ENVIRONMENT,	// creation flags
			envblock,				// environment block
			workingdirstr,		// working directory 
			&si,        		// STARTUPINFO
			&pi)        		// PROCESS_INFORMATION
		) {
		failure(env, "CreateProcess", GetLastError());
		return 0;
	}
	
	//don't need the thread handle, close it right away
	CloseHandle(pi.hThread);
	
	NativeProcess* proc = new NativeProcess(
		pi, 
		si, 
		stdoutpipein, 
		stdoutpipeout, 
		stderrpipein, 
		stderrpipeout, 
		flags, 
		reinterpret_cast<HANDLE>(interrupteventptr),
		outputconsumerref,
		errorconsumerref
	);
	
	proc->stdOutFile = stdoutfilehandlecloser.handle;
	proc->stdErrFile = stderrfilehandlecloser.handle;
	
	stdoutnamedpipe.handle = INVALID_HANDLE_VALUE;
	stdoutwritepipe.handle = INVALID_HANDLE_VALUE;
	stderrnamedpipe.handle = INVALID_HANDLE_VALUE;
	stderrwritepipe.handle = INVALID_HANDLE_VALUE;
	stdoutfilehandlecloser.handle = INVALID_HANDLE_VALUE;
	stderrfilehandlecloser.handle = INVALID_HANDLE_VALUE;

	return reinterpret_cast<jlong>(proc);
}

static char THROWAWAY_READ_BUFFER[1024 * 8];

struct PipeHandler {
	JNIEnv* env;
	HANDLE* pipeinptr;
	HANDLE* pipeoutptr;
	
	HANDLE pipein;
	
	OVERLAPPED overlapped;
	void* bufaddress;
	jlong bufcapacity;
	
	jobject processor;
	jobject bytebuffer;
	
	boolean init(JNIEnv* env, HANDLE* pipein, HANDLE* pipeout, jobject processor, jobject bytebuffer) {
		this->env = env;
		this->pipein = *pipein;
		this->pipeinptr = pipein;
		this->pipeoutptr = pipeout;
		this->processor = processor;
		this->bytebuffer = bytebuffer;
		
		if (bytebuffer != NULL) {
			bufaddress = env->GetDirectBufferAddress(bytebuffer);
			if (bufaddress == NULL) {
				failureType(env, "java/lang/IllegalArgumentException", "GetDirectBufferAddress", NULL);
				return false;
			}
			bufcapacity = env->GetDirectBufferCapacity(bytebuffer);
			if (bufcapacity <= 0) {
				failureType(env, "java/lang/IllegalArgumentException", "Illegal buffer capacity", NULL);
				return false;
			}
		} else {
			if (processor != NULL) {
				failureType(env, "java/lang/NullPointerException", "null byte buffer", NULL);
				return false;
			}
			//both processor and byte buffer are null
			//use the throwaway buffer
			bufaddress = THROWAWAY_READ_BUFFER;
			bufcapacity = sizeof(THROWAWAY_READ_BUFFER);
		}
		eventCloser = CreateEvent(NULL, FALSE, FALSE, NULL);
		if (eventCloser.handle == NULL) {
			failure(env, "CreateEvent", GetLastError());
			return false;
		}
		ZeroMemory(&overlapped, sizeof(overlapped));
		overlapped.hEvent = eventCloser.handle;
		return true;
	}
	
	bool initRead() {
		if (ioState == IO_STATE_PENDING) {
			SetLastError(ERROR_IO_PENDING);
			return false;
		}
		if (ioState == IO_STATE_CANCELLED) {
			SetLastError(ERROR_INVALID_HANDLE);
			return false;
		}
		BOOL success = ReadFile(pipein, bufaddress, bufcapacity, NULL, &overlapped);
		if (!success) {
			//completing asynchronously
			DWORD lasterror = GetLastError();
			if (lasterror != ERROR_IO_PENDING) {
				return false;
			}
		}
		ioState = IO_STATE_PENDING;
		return true;
	}
	
	bool cancelIO(){
		if (ioState == IO_STATE_CANCELLED) {
			return true;
		}
		if (!CancelIoEx(pipein, &overlapped)) {
			return false;
		}
		ioState = IO_STATE_CANCELLED;
		return true;
	}
	
	BOOL getOverlappedIOResult(LPDWORD lpNumberOfBytesTransferred, BOOL bWait){
		if (ioState == IO_STATE_NONE) {
			SetLastError(ERROR_INVALID_HANDLE);
			return FALSE;
		}
		if (GetOverlappedResult(pipein, &overlapped, lpNumberOfBytesTransferred, bWait)) {
			ioState = IO_STATE_NONE;
			return TRUE;
		}
		return FALSE;
	}
	
	void processFinished() {
		pipeInCloser = *pipeinptr;
		CloseHandle(*pipeoutptr);
		
		*pipeinptr = INVALID_HANDLE_VALUE;
		*pipeoutptr = INVALID_HANDLE_VALUE;
	}
	
	~PipeHandler() {
		//wait for the overlapped result before releasing the memory of it
		//can't really handle the result of the GetOverlappedResult call
		switch(ioState) {
			case IO_STATE_CANCELLED:{
				DWORD read;
				GetOverlappedResult(pipein, &overlapped, &read, TRUE);
				break;
			}
			case IO_STATE_PENDING: {
				if (CancelIoEx(pipein, &overlapped)) {
					DWORD read;
					GetOverlappedResult(pipein, &overlapped, &read, TRUE);
					break;
				}
				break;
			}
		}
	}
	
	bool isIOPending() const {
		return ioState != IO_STATE_NONE;
	}
	
private:
	HandleCloser eventCloser;
	
	HandleCloser pipeInCloser;
	
	static const int IO_STATE_NONE = 0;
	static const int IO_STATE_PENDING = 1;
	static const int IO_STATE_CANCELLED = 2;
	
	int ioState = IO_STATE_NONE;
};

static boolean isAnyIOPending(PipeHandler* pipes, int size) {
	for (int i = 0; i < size; ++i) {
		if (pipes[i].isIOPending()) {
			return true;
		}
	}
	return false;
}

JNIEXPORT void JNICALL Java_saker_process_platform_win32_Win32NativeProcess_native_1processIO(
	JNIEnv* env, 
	jclass clazz, 
	jlong nativeptr, 
	jobject stdoutbytedirectbuffer,
	jobject stderrbytedirectbuffer
) {
	NativeProcess* proc = reinterpret_cast<NativeProcess*>(nativeptr);
	
	if (proc->stdOutPipeIn == INVALID_HANDLE_VALUE && !proc->hasStdErrDifferentFromStdOut()) {
		//no IO to process, we can return immediately
		return;
	}
	
	jmethodID outputnotifymethod = env->GetStaticMethodID(
		clazz,
		"rewindNotifyOutput", 
		"(Ljava/nio/ByteBuffer;ILsaker/process/platform/NativeProcessIOConsumer;)V"
	);
	if (outputnotifymethod == NULL){
		failureType(env, "java/lang/AssertionError", "GetMethodID", NULL);
		return;
	}
	
	jobject stdoutprocessor = proc->standardOutputConsumer;
	jobject stderrprocessor = proc->standardErrorConsumer;
	
	int pipecount = 0;
	
	PipeHandler pipes[2];
	if (proc->stdOutPipeIn != INVALID_HANDLE_VALUE) {
		bool inited = pipes[pipecount++].init(env, &proc->stdOutPipeIn, &proc->stdOutPipeOut, stdoutprocessor, stdoutbytedirectbuffer);
		if (!inited) {
			return;
		}
	}
	if (proc->hasStdErrDifferentFromStdOut()) {
		//non merged std err
		bool inited = pipes[pipecount++].init(env, &proc->stdErrPipeIn, &proc->stdErrPipeOut, stderrprocessor, stderrbytedirectbuffer);
		if (!inited) {
			return;
		}
	}
	
	const int WAITS_OFFSET = 2;
	DWORD waitcount = WAITS_OFFSET;
	HANDLE waits[4] = { proc->procInfo.hProcess, proc->interruptEvent,  };
	for (int i = 0; i < pipecount; ++i) {
		if (!pipes[i].initRead()) {
			failure(env, "ReadFile", GetLastError());
			return;
		}
		waits[waitcount++] = pipes[i].overlapped.hEvent;
	}
	
	bool interrupted = false;
	while (true) {
		DWORD waitres = WaitForMultipleObjects(waitcount, waits, FALSE, INFINITE);
		switch(waitres) {
			case WAIT_OBJECT_0: {
				//process finished
				
				for (int i = 0; i < pipecount; ++i) {
					PipeHandler& pipe = pipes[i];
					pipe.processFinished();
					
					DWORD read = 0;
					if (!pipe.getOverlappedIOResult(&read, TRUE)) {
						DWORD lasterror = GetLastError();
						if (lasterror != ERROR_BROKEN_PIPE){
							failure(env, "GetOverlappedResult", lasterror);
							return;
						}
					} else {
						if(read > 0) {
							if (pipe.processor != NULL){
								env->CallStaticVoidMethod(clazz, outputnotifymethod, pipe.bytebuffer, read, pipe.processor);
								if (env->ExceptionCheck()) {
									return;
								}
							}
							while (ReadFile(pipe.pipein, pipe.bufaddress, pipe.bufcapacity, &read, NULL)) {
								if (read <= 0){
									break;
								}
								
								if (pipe.processor != NULL){
									env->CallStaticVoidMethod(clazz, outputnotifymethod, pipe.bytebuffer, read, pipe.processor);
									if (env->ExceptionCheck()) {
										return;
									}
								}
							}
						}
					}
				}
				return;
			}
			case WAIT_OBJECT_0 + 1: {
				//interrupted
				{
					jclass threadc = env->FindClass("java/lang/Thread");
					jmethodID interruptedmethod = env->GetStaticMethodID(threadc, "interrupted", "()Z");
					if (!env->CallStaticBooleanMethod(threadc, interruptedmethod)) {
						//false alarm? continue the loop
						continue;
					}
					//no longer needed
					env->DeleteLocalRef(threadc);
				}
				bool hadcancelfail = false;
				//we've been interrupted. cancel any pending IO, check process exit, and if we're not finished,
				//throw the interrupted exception
				for (int i = 0; i < pipecount; ++i) {
					PipeHandler& pipe = pipes[i];
					if (!pipe.isIOPending()) {
						continue;
					}
					if (!pipe.cancelIO()) {
						//failed to cancel the IO.
						//do not take the interrupt request into account
						//do not reinterrupt, as we would get into a loop
						//set internal interrupted flag so we can handle when the IO completes
						interrupted = true;
						hadcancelfail = true;
						continue;	
					}
					//IO cancellation succeeded
					//wait for the overlapped request to complete as we may not free it before returning 
					DWORD read = 0;
					if (!pipe.getOverlappedIOResult(&read, TRUE)) {
						DWORD lasterror = GetLastError();
						if(lasterror == ERROR_OPERATION_ABORTED){
							//the request was cancelled properly, without any read bytes
							continue;
						}
						if (lasterror == ERROR_IO_PENDING) {
							//shouldn't really happen, check anyway
							//we can't throw the exception, continue the loop
							interrupted = true;
							hadcancelfail = true;
							continue;
						}
						//unrecognized error
						failure(env, "GetOverlappedResult", lasterror);
						return;
					}
					if (read > 0 && pipe.processor != NULL){
						env->CallStaticVoidMethod(clazz, outputnotifymethod, pipe.bytebuffer, read, pipe.processor);
						if (env->ExceptionCheck()) {
							return;
						}
					}
				}
				if (hadcancelfail) {
					continue;
				}
				interruptException(env, "Process IO processing interrupted.");
				return;
			}
			
			default: {
				if (waitres >= WAIT_OBJECT_0 + WAITS_OFFSET && waitres < WAIT_OBJECT_0 + WAITS_OFFSET + pipecount) {
					//a pipe was notified, a read finished
					PipeHandler& pipe = pipes[waitres - (WAIT_OBJECT_0 + WAITS_OFFSET)];
					DWORD read = 0;
					if (!pipe.getOverlappedIOResult(&read, FALSE)) {
						failure(env, "GetOverlappedResult", GetLastError());
						return;
					}
					
					if (read > 0 && pipe.processor != NULL) {
						env->CallStaticVoidMethod(clazz, outputnotifymethod, pipe.bytebuffer, read, pipe.processor);
						if (env->ExceptionCheck()) {
							return;
						}
					}
					
					if (interrupted) {
						//interrupted meanwhile, dont restart IO
						if (!isAnyIOPending(pipes, pipecount)) {
							//exit the loop if there are no IO pending
							interruptException(env, "Process IO processing interrupted.");
							return;
						}
						//there is at least one IO pending, wait for it, don't restart
						break;
					}
					
					//restart read
					if (!pipe.initRead()) {
						failure(env, "ReadFile", GetLastError());
						return;
					}
				} else {
					//unknown wait result
					failure(env, "WaitForMultipleObjects", waitres);
					return;
				}
				break;
			}
		}
	}
}

static jobject createJavaLangInteger(JNIEnv* env, DWORD val) {
	jclass intclass = env->FindClass("java/lang/Integer");
	jmethodID intvalueofmethod = env->GetStaticMethodID(
		intclass,
		"valueOf", 
		"(I)Ljava/lang/Integer;"
	);
	if (intvalueofmethod == NULL){
		failureType(env, "java/lang/AssertionError", "GetMethodID", NULL);
		return NULL;
	}
	return env->CallStaticObjectMethod(intclass, intvalueofmethod, val);
}

JNIEXPORT jobject JNICALL Java_saker_process_platform_win32_Win32NativeProcess_native_1waitFor(
	JNIEnv* env, 
	jclass clazz, 
	jlong nativeptr, 
	jlong timeoutmillis
) {
	NativeProcess* proc = reinterpret_cast<NativeProcess*>(nativeptr);
	DWORD waitmillis = timeoutmillis == -1 ? INFINITE : timeoutmillis;
	
	DWORD ecode = -1;
	if (GetExitCodeProcess(proc->procInfo.hProcess, &ecode)) {
		if(ecode != STILL_ACTIVE) {
			//already finished
			return createJavaLangInteger(env, ecode);
		}
		//or it returned STILL_ACTIVE as result code 
		//wait for the process to complete
	}
	
	HANDLE waits[] = { proc->procInfo.hProcess, proc->interruptEvent };
	while(true) {
		DWORD waitres = WaitForMultipleObjects(2, waits, FALSE, waitmillis);
		switch (waitres) {
			case WAIT_OBJECT_0: {
				//process finished
				DWORD ecode = -1;
				if (GetExitCodeProcess(proc->procInfo.hProcess, &ecode)) {
					return createJavaLangInteger(env, ecode);
				}
				failure(env, "GetExitCodeProcess", GetLastError());
				return NULL;
			}
			case WAIT_OBJECT_0 + 1: {
				//interrupted event signaled
				//check if we're really interrupted
				jclass threadc = env->FindClass("java/lang/Thread");
				jmethodID interruptedmethod = env->GetStaticMethodID(threadc, "interrupted", "()Z");
				if (env->CallStaticBooleanMethod(threadc, interruptedmethod)) {
					interruptException(env, "Process waiting interrupted.");
					return NULL;
				}
				//false alarm? try waiting again
				env->DeleteLocalRef(threadc);
				continue;
			}
			case WAIT_TIMEOUT: {
				return NULL;
			}
			default: {
				failure(env, "WaitForMultipleObjects", GetLastError());
				return NULL;
			}
		}
	}
}
JNIEXPORT void JNICALL Java_saker_process_platform_win32_Win32NativeProcess_native_1interrupt(
	JNIEnv* env, 
	jclass clazz, 
	jlong interrupteventptr
) {
	HANDLE eventh = reinterpret_cast<HANDLE>(interrupteventptr);
	SetEvent(eventh);
}
JNIEXPORT jobject JNICALL Java_saker_process_platform_win32_Win32NativeProcess_native_1getExitCode(
	JNIEnv* env, 
	jclass clazz, 
	jlong nativeptr
) {
	NativeProcess* proc = reinterpret_cast<NativeProcess*>(nativeptr);
	
	DWORD ecode = -1;
	if (GetExitCodeProcess(proc->procInfo.hProcess, &ecode)) {
		if (ecode == STILL_ACTIVE) {
			//process either exited with STILL_ACTIVE return code, or is still alive.
			//check if alive and return accordingly
			DWORD ret = WaitForSingleObject(proc->procInfo.hProcess, 0);
			if (ret == WAIT_TIMEOUT) {
				failureType(env, "java/lang/IllegalThreadStateException", "GetExitCodeProcess", STILL_ACTIVE);
				return NULL;
			}
			//continue return STILL_ACTIVE
		}
		return createJavaLangInteger(env, ecode);
	}
	failure(env, "GetExitCodeProcess", GetLastError());
	return NULL;
}

JNIEXPORT void JNICALL Java_saker_process_platform_win32_Win32NativeProcess_native_1close(
	JNIEnv* env, 
	jclass clazz, 
	jlong nativeptr
) {
	NativeProcess* proc = reinterpret_cast<NativeProcess*>(nativeptr);
	
	CloseHandle(proc->procInfo.hProcess);
	if (proc->stdOutPipeIn != INVALID_HANDLE_VALUE){
		CloseHandle(proc->stdOutPipeIn);
	}
	if (proc->stdOutPipeOut != INVALID_HANDLE_VALUE){
		CloseHandle(proc->stdOutPipeOut);
	}
	if (proc->stdErrPipeIn != INVALID_HANDLE_VALUE && proc->stdErrPipeIn != proc->stdOutPipeIn) {
		CloseHandle(proc->stdErrPipeIn);
	}
	if (proc->stdErrPipeOut != INVALID_HANDLE_VALUE && proc->stdErrPipeOut != proc->stdOutPipeOut) {
		CloseHandle(proc->stdErrPipeOut);
	}
	if (proc->stdOutFile != INVALID_HANDLE_VALUE) {
		CloseHandle(proc->stdOutFile);
	}
	if (proc->stdErrFile != INVALID_HANDLE_VALUE) {
		CloseHandle(proc->stdErrFile);
	}
	if (proc->standardOutputConsumer != NULL) {
		env->DeleteGlobalRef(proc->standardOutputConsumer);
	}
	if (proc->standardErrorConsumer != NULL) {
		env->DeleteGlobalRef(proc->standardErrorConsumer);
	}
	
	delete proc;
}
JNIEXPORT jlong JNICALL Java_saker_process_platform_win32_Win32NativeProcess_native_1createInterruptEvent(
	JNIEnv* env, 
	jclass clazz
) {
	return reinterpret_cast<jlong>(CreateEvent(NULL, FALSE, FALSE, NULL));
}
JNIEXPORT void JNICALL Java_saker_process_platform_win32_Win32NativeProcess_native_1closeInterruptEvent(
	JNIEnv* env, 
	jclass clazz, 
	jlong interrupteventptr
) {
	HANDLE eventh = reinterpret_cast<HANDLE>(interrupteventptr);
	CloseHandle(eventh);
}

#define IMPLEMENTATION_JAVA_CLASS_NAME u"saker.process.platform.win32.Win32PlatformProcessFactory"

JNIEXPORT jstring JNICALL Java_saker_process_platform_NativeProcess_native_1getNativePlatformProcessFactoryImplementationClassName(
	JNIEnv* env, 
	jclass clazz
){
	return env->NewString((const jchar*)IMPLEMENTATION_JAVA_CLASS_NAME, (sizeof(IMPLEMENTATION_JAVA_CLASS_NAME)) / sizeof(char16_t) - 1);
}