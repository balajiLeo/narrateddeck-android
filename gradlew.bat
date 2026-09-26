@ECHO OFF
REM Gradle wrapper (Windows). Requires gradle\wrapper\gradle-wrapper.jar
SET DIR=%~dp0
SET JAR=%DIR%gradle\wrapper\gradle-wrapper.jar
IF NOT EXIST "%JAR%" (
  ECHO Missing %JAR%
  ECHO Open in Android Studio or run: gradle wrapper --gradle-version 8.7
  EXIT /B 1
)
IF DEFINED JAVA_HOME (
  SET JAVACMD=%JAVA_HOME%\bin\java.exe
) ELSE (
  SET JAVACMD=java.exe
)
"%JAVACMD%" -classpath "%JAR%" org.gradle.wrapper.GradleWrapperMain %*
