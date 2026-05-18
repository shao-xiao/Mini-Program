# VSCode + WeChat Mini Program Debugging

## One-time setup

1. Install WeChat DevTools.
2. Open WeChat DevTools and enable `Settings -> Security -> Service Port`.
3. If VSCode cannot find `cli.bat`, set `WECHAT_DEVTOOLS_CLI`:

```powershell
[Environment]::SetEnvironmentVariable('WECHAT_DEVTOOLS_CLI', 'C:\Program Files (x86)\Tencent\WeChat DevTools\cli.bat', 'User')
```

Restart VSCode after setting the environment variable.

## Recommended workspace

Open this workspace file in VSCode:

```text
C:\Users\Administrator\Desktop\dehui_\dehui-property.code-workspace
```

It includes:

- `dehui-property-management`: Spring Boot backend
- `dehui-property-admin`: Vue admin frontend
- `dehui-property-miniprogram`: WeChat mini program

## Run local mini program debugging

In VSCode, run `Terminal -> Run Task...`:

```text
dev: backend + miniprogram
```

This starts the backend on:

```text
http://localhost:8080/api
```

Then it opens this mini program project in WeChat DevTools.

## Mini program network config

The mini program reads its API base URL from:

```text
config/env.js
```

Current local value:

```js
baseURL: 'http://localhost:8080/api'
```

For local debugging in WeChat DevTools, keep `project.config.json` with `urlCheck: false`, or manually enable:

```text
Details -> Local Settings -> Do not verify valid domain names, web-view domains, TLS versions and HTTPS certificates
```

## Useful VSCode tasks

- `open in WeChat DevTools`: open the mini program project only.
- `preview QR`: generate a preview QR code from WeChat DevTools CLI.
- `dev: backend + miniprogram`: start backend and open mini program.
- `dev: full stack`: start backend, admin frontend, and open mini program.
