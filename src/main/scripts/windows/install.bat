@ECHO OFF

setlocal ENABLEEXTENSIONS

@ECHO Detecting Java version installed.
:CHECK_JAVA
for /f tokens^=2-5^ delims^=.-_^" %%j in ('java -fullversion 2^>^&1') do set "jver=%%j%%k"
@ECHO CurrentVersion %jver%

if %jver% NEQ 170 GOTO JAVA_NOT_INSTALLED

:JAVA_INSTALLED

@ECHO Java 17 found!
@ECHO Installing ThingsBoard remote integration example...

@ECHO ThingsBoard remote integration example installed successfully!

GOTO END

:JAVA_NOT_INSTALLED
@ECHO Java 17 is not installed. Only Java 17 is supported
@ECHO Please go to https://adoptopenjdk.net/index.html and install Java 17. Then retry installation.
PAUSE
GOTO END

:END
