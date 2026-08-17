@echo off
chcp 65001 >nul
setlocal DisableDelayedExpansion


echo.
echo ========================================
echo 德汇物业系统生产发布打包
echo ========================================


REM ================================
REM 项目路径
REM ================================

set "PROJECT_ROOT=%~dp0"

if "%PROJECT_ROOT:~-1%"=="\" (
    set "PROJECT_ROOT=%PROJECT_ROOT:~0,-1%"
)


set "BACKEND_DIR=%PROJECT_ROOT%\dehui-property-management"
set "FRONTEND_DIR=%PROJECT_ROOT%\dehui-property-admin"
set "RELEASE_DIR=%PROJECT_ROOT%\release"



for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd-HHmmss"') do (
    set "BUILD_TIME=%%i"
)


echo 项目目录:
echo %PROJECT_ROOT%

echo 后端:
echo %BACKEND_DIR%

echo 前端:
echo %FRONTEND_DIR%

echo 发布:
echo %RELEASE_DIR%

echo 时间:
echo %BUILD_TIME%

echo.



REM ================================
REM 工作树检查
REM ================================


if not exist "%PROJECT_ROOT%\AGENTS.md" (

    echo [ERROR] 当前不是德汇生产工作树

    pause
    exit /b 1

)


if not exist "%BACKEND_DIR%\pom.xml" (

    echo [ERROR] 后端pom.xml不存在

    pause
    exit /b 2

)


if not exist "%FRONTEND_DIR%\package.json" (

    echo [ERROR] 前端package.json不存在

    pause
    exit /b 3

)


echo [OK] 项目结构正常



REM ================================
REM 环境检查
REM ================================


echo.
echo ========================================
echo 检查环境
echo ========================================


where java >nul 2>&1

if errorlevel 1 (
    echo [ERROR] Java不存在
    exit /b 10
)



where node >nul 2>&1

if errorlevel 1 (
    echo [ERROR] Node不存在
    exit /b 11
)



where npm >nul 2>&1

if errorlevel 1 (
    echo [ERROR] npm不存在
    exit /b 12
)



set "MAVEN_CMD="


for /f "delims=" %%i in ('where mvn 2^>nul') do (

    if not defined MAVEN_CMD (
        set "MAVEN_CMD=%%i"
    )

)



if not defined MAVEN_CMD (

    echo 系统Maven不存在
    echo 搜索Codex Maven...


    for /f "delims=" %%i in ('where /r "%USERPROFILE%\.cache\codex-runtimes" mvn.cmd 2^>nul') do (

        if not defined MAVEN_CMD (
            set "MAVEN_CMD=%%i"
        )

    )

)



if not defined MAVEN_CMD (

    echo [ERROR] Maven不存在

    pause
    exit /b 13

)



echo Maven:
echo %MAVEN_CMD%


java -version

call "%MAVEN_CMD%" -version

node -v

call npm -v



REM ================================
REM 创建发布目录
REM ================================


if not exist "%RELEASE_DIR%" (

    mkdir "%RELEASE_DIR%"

)



set "APP_JAR=%RELEASE_DIR%\app.jar"
set "APP_VERSION=%RELEASE_DIR%\app-%BUILD_TIME%.jar"

set "DIST_ZIP=%RELEASE_DIR%\dist.zip"
set "DIST_VERSION=%RELEASE_DIR%\dist-%BUILD_TIME%.zip"



REM ================================
REM 后端
REM ================================


echo.
echo ========================================
echo Spring Boot 后端打包
echo ========================================


cd /d "%BACKEND_DIR%"



echo 当前目录:
echo %CD%



echo 执行升级保护测试...


call "%MAVEN_CMD%" -q -Dtest=BusinessDataUpgradePreservationPolicyTest,RbacUpgradePreservationPolicyTest test


if errorlevel 1 (

    echo [ERROR] 数据保护测试失败

    exit /b 20

)


echo [OK] 数据保护测试通过




echo Maven package...


call "%MAVEN_CMD%" package -DskipTests



if errorlevel 1 (

    echo [ERROR] 后端打包失败

    exit /b 21

)



echo [OK] 后端打包成功



REM ================================
REM 复制JAR
REM ================================


set "BUILT_JAR="


for /f "delims=" %%f in ('dir /b /a-d /o-d "%BACKEND_DIR%\target\*.jar" 2^>nul') do (

    if not defined BUILT_JAR set "BUILT_JAR=%BACKEND_DIR%\target\%%f"

)


if not defined BUILT_JAR (

    echo [ERROR] No publishable JAR was found

    exit /b 22

)


copy /y "%BUILT_JAR%" "%APP_JAR%" >nul

copy /y "%BUILT_JAR%" "%APP_VERSION%" >nul


powershell -NoProfile -Command "$buildTime = Get-Date; (Get-Item -LiteralPath '%APP_JAR%').LastWriteTime = $buildTime; (Get-Item -LiteralPath '%APP_VERSION%').LastWriteTime = $buildTime"


if errorlevel 1 (

    echo [ERROR] Failed to refresh JAR modified time

    exit /b 23

)


if not exist "%APP_JAR%" (

    echo [ERROR] 没找到app.jar

    exit /b 22

)


echo [OK] app.jar生成



REM ================================
REM 前端
REM ================================


echo.
echo ========================================
echo Vue 前端打包
echo ========================================


cd /d "%FRONTEND_DIR%"



if not exist node_modules (

    echo 安装依赖...

    call npm install

)



call npm run build



if errorlevel 1 (

    echo [ERROR] 前端构建失败

    exit /b 30

)



if not exist dist\index.html (

    echo [ERROR] dist生成失败

    exit /b 31

)



echo [OK] 前端构建完成



powershell -NoProfile -Command "Compress-Archive -Path '%FRONTEND_DIR%\dist' -DestinationPath '%DIST_ZIP%' -Force"



copy /y "%DIST_ZIP%" "%DIST_VERSION%" >nul



echo [OK] dist.zip生成



REM ================================
REM 版本信息
REM ================================


cd /d "%PROJECT_ROOT%"


(
echo 德汇物业系统发布信息
echo.
echo 时间:
echo %BUILD_TIME%
echo.
echo Git Commit:
git rev-parse --short HEAD
echo.
echo Branch:
git branch --show-current
echo.
echo Backend:
echo app.jar
echo.
echo Frontend:
echo dist.zip

)> "%RELEASE_DIR%\version-info.txt"



echo.
echo ========================================
echo 发布完成
echo ========================================


dir "%RELEASE_DIR%"


pause

exit /b 0
