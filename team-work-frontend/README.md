# team-work-frontend

首版前端：Vue 3 + TypeScript + Vite + Element Plus。开发时代理 `/api` 到 `http://127.0.0.1:8080`。

## 启动

本机 Node 在 `E:\DEVELOP\nodejs`。pnpm 可用 corepack 缓存中的 11.25.0。

```powershell
$env:PATH = "E:\DEVELOP\nodejs;" + $env:PATH
cd E:\DEVELOP\Team-Work\team-work-frontend
node C:\Users\18143\AppData\Local\node\corepack\v1\pnpm\11.25.0\bin\pnpm.cjs install
node C:\Users\18143\AppData\Local\node\corepack\v1\pnpm\11.25.0\bin\pnpm.cjs dev
```

若 PATH 已包含 `pnpm` 与 `node`：

```powershell
cd E:\DEVELOP\Team-Work\team-work-frontend
pnpm install
pnpm dev
```

浏览器打开 http://127.0.0.1:5173 。后端需先在 8080 启动。
