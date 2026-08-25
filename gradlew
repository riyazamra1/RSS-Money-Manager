#!/bin/sh

APP_HOME=${0%"${0##*/}"}
APP_HOME=$( cd -P "${APP_HOME:-./}" > /dev/null && printf '%s\n' "$PWD" ) || exit
APP_BASE_NAME=${0##*/}

# RSS Money Manager: isolate CodeOnTheGo/AndroidIDE Ubuntu ARM64 builds from
# AndroidIDE's incompatible x86 Linux AAPT2 cache.
RSS_MONEY_MANAGER_ROOT="${RSS_MONEY_MANAGER_ROOT:-/root/.local/share/rss-money-manager}"
RSS_MONEY_MANAGER_ANDROIDIDE_HOME="/data/data/com.itsaky.androidide/files/home"
RSS_MONEY_MANAGER_SDK="${RSS_MONEY_MANAGER_SDK:-$RSS_MONEY_MANAGER_ROOT/android-sdk}"

# On ARM64 proot, always use an Ubuntu-local Gradle home. This prevents AGP
# from discovering AndroidIDE's transformed x86 AAPT2 binaries.
if [ "$(uname -m 2>/dev/null)" = "aarch64" ] && [ -d "$RSS_MONEY_MANAGER_ANDROIDIDE_HOME" ]; then
  GRADLE_USER_HOME="$RSS_MONEY_MANAGER_ROOT/gradle-home"
elif [ -z "${GRADLE_USER_HOME:-}" ]; then
  GRADLE_USER_HOME="$APP_HOME/.gradle-ubuntu"
fi
export GRADLE_USER_HOME

if [ -f "$RSS_MONEY_MANAGER_SDK/platforms/android-36/android.jar" ]; then
  ANDROID_HOME="$RSS_MONEY_MANAGER_SDK"
  ANDROID_SDK_ROOT="$RSS_MONEY_MANAGER_SDK"
  export ANDROID_HOME ANDROID_SDK_ROOT
fi

RSS_MONEY_MANAGER_AAPT2="${RSS_MONEY_MANAGER_AAPT2:-$RSS_MONEY_MANAGER_ROOT/android-tools/aapt2}"
if [ ! -x "$RSS_MONEY_MANAGER_AAPT2" ] && [ -x "$RSS_MONEY_MANAGER_ROOT/android-tools/aapt2-v35-arm64-v8a" ]; then
  RSS_MONEY_MANAGER_AAPT2="$RSS_MONEY_MANAGER_ROOT/android-tools/aapt2-v35-arm64-v8a"
fi
if [ ! -x "$RSS_MONEY_MANAGER_AAPT2" ]; then
  for candidate in \
    "$RSS_MONEY_MANAGER_SDK/build-tools/35.0.0/aapt2" \
    "$RSS_MONEY_MANAGER_SDK/build-tools/35.0.1/aapt2" \
    "$RSS_MONEY_MANAGER_SDK/build-tools/36.0.0/aapt2"; do
    if [ -x "$candidate" ]; then RSS_MONEY_MANAGER_AAPT2="$candidate"; break; fi
  done
fi

if [ -x "$RSS_MONEY_MANAGER_AAPT2" ]; then
  mkdir -p "$GRADLE_USER_HOME"
  AAPT2_PROPS="$GRADLE_USER_HOME/gradle.properties"
  if [ -f "$AAPT2_PROPS" ]; then
    if grep -q '^android\.aapt2FromMavenOverride=' "$AAPT2_PROPS"; then
      sed -i "s#^android\.aapt2FromMavenOverride=.*#android.aapt2FromMavenOverride=$RSS_MONEY_MANAGER_AAPT2#" "$AAPT2_PROPS"
    else
      printf '\nandroid.aapt2FromMavenOverride=%s\n' "$RSS_MONEY_MANAGER_AAPT2" >> "$AAPT2_PROPS"
    fi
  else
    printf 'android.aapt2FromMavenOverride=%s\n' "$RSS_MONEY_MANAGER_AAPT2" > "$AAPT2_PROPS"
  fi
  set -- "-Pandroid.aapt2FromMavenOverride=$RSS_MONEY_MANAGER_AAPT2" "$@"
fi

MAX_FD=maximum
warn () { echo "$*" >&2; }
die () { echo >&2; echo "$*" >&2; echo >&2; exit 1; }
cygwin=false; msys=false; darwin=false; nonstop=false
case "$( uname )" in
  CYGWIN* ) cygwin=true ;; Darwin* ) darwin=true ;;
  MSYS* | MINGW* ) msys=true ;; NONSTOP* ) nonstop=true ;;
esac
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

if [ -n "$JAVA_HOME" ]; then
  if [ -x "$JAVA_HOME/jre/sh/java" ]; then JAVACMD=$JAVA_HOME/jre/sh/java; else JAVACMD=$JAVA_HOME/bin/java; fi
  [ -x "$JAVACMD" ] || die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME"
else
  JAVACMD=java
  command -v java >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH."
fi

if ! "$cygwin" && ! "$darwin" && ! "$nonstop"; then
  case $MAX_FD in max*) MAX_FD=$( ulimit -H -n ) || warn "Could not query maximum file descriptor limit" ;; esac
  case $MAX_FD in '' | soft) :;; *) ulimit -n "$MAX_FD" || warn "Could not set maximum file descriptor limit to $MAX_FD" ;; esac
fi

if "$cygwin" || "$msys"; then
  APP_HOME=$( cygpath --path --mixed "$APP_HOME" )
  CLASSPATH=$( cygpath --path --mixed "$CLASSPATH" )
  JAVACMD=$( cygpath --unix "$JAVACMD" )
  for arg do
    case $arg in -*) ;; /?*) t=${arg#/}; t=/${t%%/*}; [ -e "$t" ] && arg=$( cygpath --path --ignore --mixed "$arg" ) ;; esac
    shift; set -- "$@" "$arg"
  done
fi

DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'
set -- "-Dorg.gradle.appname=$APP_BASE_NAME" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
command -v xargs >/dev/null 2>&1 || die "xargs is not available"
eval "set -- $(printf '%s\n' "$DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS" | xargs -n1 | sed ' s~[^-[:alnum:]+,./:=@_]~\\&~g; ' | tr '\n' ' ')" '"$@"'
exec "$JAVACMD" "$@"
