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
	HANDLE stdInPipeIn;
	HANDLE stdInPipeOut;
	jint flags;
	
	jobject standardOutputConsumer; 
	jobject standardErrorConsumer;
	
	HANDLE stdOutFile = INVALID_HANDLE_VALUE;
	HANDLE stdErrFile = INVALID_HANDLE_VALUE;
	HANDLE stdInFile = INVALID_HANDLE_VALUE;
	
	NativeProcess(
			const PROCESS_INFORMATION& pi, 
			const STARTUPINFOW& si, 
			HANDLE stdoutpipein, 
			HANDLE stdoutpipeout,
			HANDLE stderrpipein, 
			HANDLE stderrpipeout,
			HANDLE stdinpipein,
			HANDLE stdinpipeout,
			jint flags,
			jobject standardOutputConsumer,
			jobject standardErrorConsumer)
		: 
			procInfo(pi), 
			startupInfo(si),
			stdOutPipeIn(stdoutpipein),
			stdOutPipeOut(stdoutpipeout),
			stdErrPipeIn(stderrpipein),
			stdErrPipeOut(stderrpipeout),
			stdInPipeIn(stdinpipein),
			stdInPipeOut(stdinpipeout),
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
struct JniGlobalRef {
	JNIEnv *env;
	jobject ref;

	JniGlobalRef(JNIEnv *env, jobject ref = NULL) :
			env(env), ref(ref) {
	}
	JniGlobalRef(const JniGlobalRef&) = delete;
	JniGlobalRef(JniGlobalRef&&) = delete;

	~JniGlobalRef() {
		if (ref != NULL) {
			env->DeleteGlobalRef(ref);
		}
	}
};

struct NulFileHandle {
	HandleCloser handle;

	NulFileHandle() {
		SECURITY_ATTRIBUTES secattrs_inherit_handle = { };
		secattrs_inherit_handle.nLength = sizeof(SECURITY_ATTRIBUTES);
		secattrs_inherit_handle.bInheritHandle = TRUE;

		HANDLE h = CreateFileW(
				L"NUL",
				GENERIC_READ | GENERIC_READ,
				FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE,
				&secattrs_inherit_handle,
				OPEN_EXISTING,
				FILE_ATTRIBUTE_NORMAL,
				NULL);
		this->handle = h;
	}

	operator HANDLE() const {
		return handle.handle;
	}
};
static const NulFileHandle NUL_FILE_HANDLE;

#define PIPE_NAME_PREFIX "\\\\.\\pipe\\"

static HANDLE createNamedPipeWithName(const char* pipename, LPSECURITY_ATTRIBUTES lpSecurityAttributes) {
	return CreateNamedPipe(
			pipename,
			PIPE_ACCESS_INBOUND | FILE_FLAG_FIRST_PIPE_INSTANCE | FILE_FLAG_OVERLAPPED,
			PIPE_TYPE_BYTE | PIPE_READMODE_BYTE | PIPE_WAIT | PIPE_REJECT_REMOTE_CLIENTS,
			1,
			Java_const_saker_process_platform_win32_Win32NativeProcess_DEFAULT_IO_PROCESSING_DIRECT_BUFFER_SIZE,
			Java_const_saker_process_platform_win32_Win32NativeProcess_DEFAULT_IO_PROCESSING_DIRECT_BUFFER_SIZE,
			INFINITE,
			lpSecurityAttributes
		);
}
static HANDLE createNamedPipeWithName(const char* pipename) {
	return createNamedPipeWithName(pipename, NULL);
}
static HANDLE openPipeWrite(const char* pipename, LPSECURITY_ATTRIBUTES lpSecurityAttributes) {
	return CreateFile(
		pipename,
		GENERIC_WRITE,
		0,
		lpSecurityAttributes,
		OPEN_EXISTING,
		0,
		NULL
	);
}
static HANDLE openPipeWrite(const char* pipename) {
	// To inherit the handles the bInheritHandle flag so pipe handles are inherited.
	SECURITY_ATTRIBUTES secattrs_inherit_handle = {};
	secattrs_inherit_handle.nLength = sizeof(SECURITY_ATTRIBUTES);
	secattrs_inherit_handle.bInheritHandle = TRUE;

	return openPipeWrite(pipename, &secattrs_inherit_handle);
}

#define FLAG_IS_MERGE_STDERR(flags) ((flags & Java_const_saker_process_platform_NativeProcess_FLAG_MERGE_STDERR) == Java_const_saker_process_platform_NativeProcess_FLAG_MERGE_STDERR)
#define FLAG_IS_PIPE_STDIN(flags) ((flags & Java_const_saker_process_platform_NativeProcess_FLAG_PIPE_STDIN) == Java_const_saker_process_platform_NativeProcess_FLAG_PIPE_STDIN)

JNIEXPORT jlong JNICALL Java_saker_process_platform_win32_Win32NativeProcess_native_1startProcess(
	JNIEnv* env, 
	jclass clazz, 
	jstring exe, 
	jobjectArray commands, 
	jstring workingdirectory,
	jint flags, 
	jstring pipeid,
	jstring envstr,
	jobject standardOutputConsumer, 
	jobject standardErrorConsumer, 
	jstring stdoutfilepath, 
	jstring stderrfilepath,
	jstring stdinfilepath
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
	HandleCloser stdinnamedpipe;
	HandleCloser stdinwritepipe;
	
	HandleCloser stdoutfilehandlecloser;
	HandleCloser stderrfilehandlecloser;
	HandleCloser stdinfilehandlecloser;
	
	HANDLE stdoutpipein = INVALID_HANDLE_VALUE;
	HANDLE stdoutpipeout = INVALID_HANDLE_VALUE;
	HANDLE stderrpipein = INVALID_HANDLE_VALUE;
	HANDLE stderrpipeout = INVALID_HANDLE_VALUE;
	HANDLE stdinpipein = INVALID_HANDLE_VALUE;
	HANDLE stdinpipeout = INVALID_HANDLE_VALUE;
	
	//set the defaults to the NUL file
	//so the writes won't fail in the child process
	si.hStdOutput = NUL_FILE_HANDLE;
	si.hStdError = NUL_FILE_HANDLE;
	si.hStdInput = NUL_FILE_HANDLE;
	si.dwFlags |= STARTF_USESTDHANDLES;

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
		
	    HANDLE h = CreateFileW(
			fname.c_str(),
	        GENERIC_WRITE,
	        FILE_SHARE_READ,
	        &secattrs_inherit_handle,
			OPEN_ALWAYS,
	        FILE_ATTRIBUTE_NORMAL,
	        NULL 
        );
        if (h == INVALID_HANDLE_VALUE) {
        	failure(env, "Failed to open standard output file: (CreateFile)", GetLastError());
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
		
	    HANDLE h = CreateFileW(
			fname.c_str(),
	        GENERIC_WRITE,
	        FILE_SHARE_READ,
	        &secattrs_inherit_handle,
			OPEN_ALWAYS,
	        FILE_ATTRIBUTE_NORMAL,
	        NULL 
        );
        if (h == INVALID_HANDLE_VALUE) {
        	failure(env, "Failed to open standard error file: (CreateFile)", GetLastError());
			return 0;
        }
        stderrfilehandlecloser = h;
        //truncate the file if it was longer when opened
        SetEndOfFile(h);
        si.hStdError = h;
	}
	if (FLAG_IS_PIPE_STDIN(flags)) {
		failureType(env, "java/lang/UnsupportedOperationException", "Standard input piping unsupported.", NULL);
		return 0;
	} else if (stdinfilepath != NULL) {
		std::wstring fname = Java_To_WStr(env, stdinfilepath);

		SECURITY_ATTRIBUTES secattrs_inherit_handle = {};
		secattrs_inherit_handle.nLength = sizeof(SECURITY_ATTRIBUTES);
		secattrs_inherit_handle.bInheritHandle = TRUE;

		HANDLE h = CreateFileW(
			fname.c_str(),
			GENERIC_READ,
			FILE_SHARE_READ,
			&secattrs_inherit_handle,
			OPEN_EXISTING,
			FILE_ATTRIBUTE_NORMAL,
			NULL
		);
		if (h == INVALID_HANDLE_VALUE) {
			failure(env, "Failed to open standard input file: (CreateFile)", GetLastError());
			return 0;
		}
		stdinfilehandlecloser = h;
		si.hStdInput = h;
	}
	
	std::wstring modname = Java_To_WStr(env, exe);
	std::wstring workdir = Java_To_WStr(env, workingdirectory);
	std::wstring environmentwstr = Java_To_WStr(env, envstr);
	jsize cmdlen = commands == NULL ? 0 : env->GetArrayLength(commands);
	std::wstring cmdstr;

	//include the executable as the zeroth argument
 	if (exe != NULL) {
 		ArgvQuote(modname, cmdstr, false);
 	}
	for (jsize i = 0; i < cmdlen; ++i) {
 		jstring c = static_cast<jstring>(env->GetObjectArrayElement(commands, i));
 		std::wstring cstr = Java_To_WStr(env, c);
		if (cmdstr.length() > 0) {
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
	
	JniGlobalRef outputconsumerrefcloser(env);
	JniGlobalRef errorconsumerrefcloser(env);

	if (standardOutputConsumer != NULL) {
		outputconsumerrefcloser.ref = env->NewGlobalRef(standardOutputConsumer);
		if (outputconsumerrefcloser.ref == NULL) {
			javaException(env, "java/lang/OutOfMemoryError",
					"Failed to create JNI global reference.");
			return 0;
		}
	}
	if (standardErrorConsumer != NULL) {
		errorconsumerrefcloser.ref = env->NewGlobalRef(standardErrorConsumer);
		if (errorconsumerrefcloser.ref == NULL) {
			javaException(env, "java/lang/OutOfMemoryError",
					"Failed to create JNI global reference.");
			return 0;
		}
	}

	//about the console management of the subprocess
	//if the current process has no console, then we need to
	// specify CREATE_NO_WINDOW so the child doesn't arbitrarily create
	// sub consoles, and they won't just pop up randomly during builds
	//However, the CREATE_NO_WINDOW flag adds like +15 ms to the startup time
	// of the process, so we don't want to use it if we can
	//The flag DETACHED_PROCESS is not suitable, as that allows
	// the child process to create consoles and pop them up randomly
	//So:
	// if the current process already has a console
	// then we don't specify any other flags to avoid the
	// time overhead of CREATE_NO_WINDOW
	//
	// if the current process HAS a console, then
	// we inherit form that and the subprocess can start quickly
	//
	//the processHasConsole variable is stored statically so we don't query it
	// for every process creation
	// we expect that it doesn't get modified during the lifetime of the process
	// as AllocConsole and FreeConsole calls usually occurr during initialization
	// and destruction
	static bool processHasConsole = GetConsoleWindow() != NULL;
	DWORD creationFlags = CREATE_UNICODE_ENVIRONMENT;
	if (!processHasConsole) {
		creationFlags |= CREATE_NO_WINDOW;
	}

	if (!CreateProcessW(
			modnamestr,			// exe path
			cmd,				// Command line
			NULL,       		// process security attributes
			NULL,       		// thread security attributes
			TRUE,      			// handle inheritance
			creationFlags,	// creation flags
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
		stdinpipein,
		stdinpipeout,
		flags, 
		outputconsumerrefcloser.ref,
		errorconsumerrefcloser.ref
	);
	
	outputconsumerrefcloser.ref = NULL;
	errorconsumerrefcloser.ref = NULL;

	proc->stdOutFile = stdoutfilehandlecloser.handle;
	proc->stdErrFile = stderrfilehandlecloser.handle;
	proc->stdInFile = stdinfilehandlecloser.handle;
	
	stdoutnamedpipe.handle = INVALID_HANDLE_VALUE;
	stdoutwritepipe.handle = INVALID_HANDLE_VALUE;
	stderrnamedpipe.handle = INVALID_HANDLE_VALUE;
	stderrwritepipe.handle = INVALID_HANDLE_VALUE;
	stdinnamedpipe.handle = INVALID_HANDLE_VALUE;
	stdinwritepipe.handle = INVALID_HANDLE_VALUE;
	stdoutfilehandlecloser.handle = INVALID_HANDLE_VALUE;
	stderrfilehandlecloser.handle = INVALID_HANDLE_VALUE;
	stdinfilehandlecloser.handle = INVALID_HANDLE_VALUE;

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
		if (ioState != IO_STATE_NONE) {
			SetLastError(ERROR_IO_PENDING);
			return false;
		}
		BOOL success = ReadFile(pipein, bufaddress, bufcapacity, NULL, &overlapped);
		if (!success) {
			DWORD lasterror = GetLastError();
			if (lasterror != ERROR_IO_PENDING) {
				return false;
			}
			//completing asynchronously
		}
		ioState = IO_STATE_PENDING;
		return true;
	}
	
	bool performSynchronousRead(LPDWORD lpNumberOfBytesTransferred) {
		if (ioState == IO_STATE_PENDING) {
			return getOverlappedIOResult(lpNumberOfBytesTransferred, TRUE);
		}
		if (ioState == IO_STATE_CANCELLED) {
			SetLastError(ERROR_OPERATION_ABORTED);
			return false;
		}
		BOOL success = ReadFile(pipein, bufaddress, bufcapacity, lpNumberOfBytesTransferred, &overlapped);
		if (success) {
			//the number of bytes are written to the buffer
			return true;
		}
		DWORD lasterror = GetLastError();
		if (lasterror != ERROR_IO_PENDING) {
			//some other error
			return false;
		}
		//completing asynchronously
		return getOverlappedIOResult(lpNumberOfBytesTransferred, TRUE);
	}

	bool isIOPending() const {
		return ioState != IO_STATE_NONE;
	}
	bool isIOCancelled() const {
		return ioState == IO_STATE_CANCELLED;
	}

	bool cancelIO() {
		if (ioState == IO_STATE_NONE) {
			return true;
		}
		if (ioState == IO_STATE_CANCELLED) {
			return true;
		}
		if (!CancelIoEx(pipein, &overlapped)) {
			if (GetLastError() == ERROR_NOT_FOUND) {
				ioState = IO_STATE_NONE;
			}
			return false;
		}
		//successful cancel, will need to wait for it
		ioState = IO_STATE_CANCELLED;
		return true;
	}
	
	BOOL getOverlappedIOResult(LPDWORD lpNumberOfBytesTransferred, BOOL bWait){
		if (ioState == IO_STATE_NONE) {
			lpNumberOfBytesTransferred = 0;
			SetLastError(ERROR_SUCCESS);
			return TRUE;
		}
		if (GetOverlappedResult(pipein, &overlapped, lpNumberOfBytesTransferred, bWait)) {
			ioState = IO_STATE_NONE;
			return TRUE;
		}
		DWORD lasterror = GetLastError();
		if (lasterror == ERROR_OPERATION_ABORTED) {
			//the operation was aborted
			//return false, but also reset the state as this error signals the cancellation of the operation
			ioState = IO_STATE_NONE;
			return FALSE;
		}
		return FALSE;
	}
	
	void processFinished() {
		pipeInCloser = *pipeinptr;
		BOOL discres = DisconnectNamedPipe(pipein);
		CloseHandle(*pipeoutptr);
		
		*pipeinptr = INVALID_HANDLE_VALUE;
		*pipeoutptr = INVALID_HANDLE_VALUE;
	}
	
	~PipeHandler() {
		//wait for the overlapped result before releasing the memory of it
		//can't really handle the result of the GetOverlappedResult call
		switch (ioState) {
			case IO_STATE_CANCELLED:{
				DWORD read;
				GetOverlappedResult(pipein, &overlapped, &read, TRUE);
				break;
			}
			case IO_STATE_PENDING: {
				BOOL cancelres = CancelIoEx(pipein, &overlapped);
				if (cancelres || GetLastError() != ERROR_NOT_FOUND) {
					DWORD read;
					GetOverlappedResult(pipein, &overlapped, &read, TRUE);
				}
				break;
			}
		}
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
	jobject stderrbytedirectbuffer,
	jlong interrupteventptr
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
	HANDLE waits[4] = { proc->procInfo.hProcess, reinterpret_cast<HANDLE>(interrupteventptr),  };
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
		switch (waitres) {
			case WAIT_OBJECT_0: {
				//process finished
				
				for (int i = 0; i < pipecount; ++i) {
					//perform a non-waiting overlapped result retrieval before finishing the pipes
					PipeHandler& pipe = pipes[i];
					DWORD read = 0;
					BOOL overlappedres = pipe.getOverlappedIOResult(&read, FALSE);
					if (read > 0 && pipe.processor != NULL){
						env->CallStaticVoidMethod(clazz, outputnotifymethod, pipe.bytebuffer, read, pipe.processor);
						if (env->ExceptionCheck()) {
							return;
						}
					}
				}
				for (int i = 0; i < pipecount; ++i) {
					PipeHandler& pipe = pipes[i];
					pipe.processFinished();
				}
				for (int i = 0; i < pipecount; ++i) {
					PipeHandler& pipe = pipes[i];
					
					DWORD read = 0;
					BOOL overlappedres = pipe.getOverlappedIOResult(&read, TRUE);
					if (read > 0 && pipe.processor != NULL){
						env->CallStaticVoidMethod(clazz, outputnotifymethod, pipe.bytebuffer, read, pipe.processor);
						if (env->ExceptionCheck()) {
							return;
						}
					}
					if(!overlappedres) {
						DWORD lasterror = GetLastError();
						if (lasterror != ERROR_BROKEN_PIPE && lasterror != ERROR_PIPE_NOT_CONNECTED && lasterror != ERROR_OPERATION_ABORTED) {
							failure(env, "GetOverlappedResult", lasterror);
							return;
						}
					}
					if (read <= 0 || pipe.processor == NULL) {
						continue;
					}
					//read the remaining data
					while (true) {
						read = 0;
						BOOL readres = pipe.performSynchronousRead(&read);
						if (!readres) {
							DWORD lasterror = GetLastError();
							if (lasterror != ERROR_BROKEN_PIPE && lasterror != ERROR_PIPE_NOT_CONNECTED && lasterror != ERROR_OPERATION_ABORTED) {
								failure(env, "GetOverlappedResult", lasterror);
								return;
							}
							break;
						}
						if (read <= 0){
							break;
						}

						env->CallStaticVoidMethod(clazz, outputnotifymethod, pipe.bytebuffer, read, pipe.processor);
						if (env->ExceptionCheck()) {
							return;
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

				//cancel all IO
				for (int i = 0; i < pipecount; ++i) {
					PipeHandler& pipe = pipes[i];
					if (!pipe.isIOPending()) {
						continue;
					}
					if (pipe.cancelIO()) {
						continue;
					}
					//failed to cancel the IO.
					if (GetLastError() == ERROR_NOT_FOUND) {
						//the operation was not found
						continue;
					}

					//check if we can get the overlapped result without waiting
					DWORD read = 0;
					BOOL overlappedres = pipe.getOverlappedIOResult(&read, FALSE);
					if (read > 0 && pipe.processor != NULL){
						env->CallStaticVoidMethod(clazz, outputnotifymethod, pipe.bytebuffer, read, pipe.processor);
						if (env->ExceptionCheck()) {
							return;
						}
					}
					if (overlappedres || GetLastError() == ERROR_OPERATION_ABORTED) {
						//the overlapped IO succeeded, or cancelled successfully
						continue;
					}
					//do not take the interrupt request into account
					//do not reinterrupt, as we would get into a loop
					//set internal interrupted flag so we can handle when the IO completes
					interrupted = true;
					hadcancelfail = true;
					continue;
				}
				for (int i = 0; i < pipecount; ++i) {
					PipeHandler& pipe = pipes[i];
					if (!pipe.isIOCancelled()) {
						//do not attempt to wait for IO that is not cancelled
						continue;
					}
					//the IO was successfully cancelled. wait for it
					//wait for the overlapped request to complete as we may not free it before returning 
					DWORD read = 0;
					BOOL overlappedres = pipe.getOverlappedIOResult(&read, TRUE);
					if (read > 0 && pipe.processor != NULL){
						env->CallStaticVoidMethod(clazz, outputnotifymethod, pipe.bytebuffer, read, pipe.processor);
						if (env->ExceptionCheck()) {
							return;
						}
					}
					if (overlappedres) {
						//successful cancellation
						continue;
					}
					DWORD lasterror = GetLastError();
					if (lasterror == ERROR_OPERATION_ABORTED) {
						//the request was cancelled properly
						continue;
					}
					if (lasterror == ERROR_IO_PENDING) {
						//shouldn't really happen, check anyway
						//failed to cancel the request, still running
						interrupted = true;
						hadcancelfail = true;
						continue;
					}

					//unrecognized error
					failure(env, "GetOverlappedResult", lasterror);
					return;
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
					BOOL overlappedres = pipe.getOverlappedIOResult(&read, FALSE);
					
					if (read > 0 && pipe.processor != NULL) {
						env->CallStaticVoidMethod(clazz, outputnotifymethod, pipe.bytebuffer, read, pipe.processor);
						if (env->ExceptionCheck()) {
							return;
						}
					}

					if (!overlappedres) {
						if (GetLastError() == ERROR_OPERATION_ABORTED) {
							//cancelled IO
						}else{
							failure(env, "GetOverlappedResult", GetLastError());
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
	jlong timeoutmillis,
	jlong interrupteventptr
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
	
	HANDLE waits[] = { proc->procInfo.hProcess, reinterpret_cast<HANDLE>(interrupteventptr) };
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
	if (proc->stdInPipeIn != INVALID_HANDLE_VALUE){
		CloseHandle(proc->stdInPipeIn);
	}
	if (proc->stdInPipeOut != INVALID_HANDLE_VALUE){
		CloseHandle(proc->stdInPipeOut);
	}
	if (proc->stdOutFile != INVALID_HANDLE_VALUE) {
		CloseHandle(proc->stdOutFile);
	}
	if (proc->stdErrFile != INVALID_HANDLE_VALUE) {
		CloseHandle(proc->stdErrFile);
	}
	if (proc->stdInFile != INVALID_HANDLE_VALUE) {
		CloseHandle(proc->stdInFile);
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
	HANDLE eventh = CreateEvent(NULL, FALSE, FALSE, NULL);
	if(eventh == NULL) {
		failureType(env, "java/lang/RuntimeException", "Failed to create interrupt event.", GetLastError());
		return NULL;
	}
	return reinterpret_cast<jlong>(eventh);
}
JNIEXPORT void JNICALL Java_saker_process_platform_win32_Win32NativeProcess_native_1closeInterruptEvent(
	JNIEnv* env, 
	jclass clazz, 
	jlong interrupteventptr
) {
	if (interrupteventptr == NULL) {
		return;
	}
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
