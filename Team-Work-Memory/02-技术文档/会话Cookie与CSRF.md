---
title: 会话 Cookie 与 CSRF
date: 2026-11-06
tags: [接口, 安全, 前端联调]
status: 已实测
---

# 会话 Cookie 与 CSRF

记录日期：2026-11-06。来源：为 Vue/Vite 开发服务联调后端会话。适用范围：首版本机开发。验证见 [[03-开发记录/2026-11-06-成员列表与SPA会话]]。

## 已确认

登录态是服务端 Session，Cookie 名默认 `JSESSIONID`。`HttpOnly` 为 true，`Path` 为 `/`，`SameSite` 为 `Lax`。前端不要改这个 Cookie，请求带上即可。

写操作需要 CSRF。先 `GET /api/auth/csrf`，响应 JSON 为 `token` 和 `headerName`（值为 `X-XSRF-TOKEN`）。同时会下发可读 Cookie `XSRF-TOKEN`，路径为 `/`，`SameSite=Lax`。不要把口令或 token 写进仓库。

## 前端应如何传 Cookie 和 CSRF

两种开发方式都支持，优先 CORS。

1. 浏览器直接请求后端（例如页面 `http://localhost:5173`，接口 `http://localhost:8080`）：
   - 所有请求设置 `credentials: 'include'`（axios：`withCredentials: true`）。
   - 先请求 `GET /api/auth/csrf`，把返回的 `token` 放到后续写请求的 `X-XSRF-TOKEN` 头。
   - 页面和接口都用 `localhost`，或都用 `127.0.0.1`，不要混用；混用不是同站，`Lax` Cookie 不会带上。
2. Vite 把 `/api` 代理到后端：浏览器只访问前端源，Cookie 是同站同源，仍要带 CSRF 头。代理需转发 Cookie，不要改写 `Set-Cookie`。

`GET` 读接口只需 Cookie，不必带 CSRF 头。`POST`/`PUT`/`PATCH`/`DELETE` 必须带 CSRF 头，并继续带 Cookie。

允许的开发源默认是 `http://localhost:5173` 和 `http://127.0.0.1:5173`。改端口时用环境变量 `CORS_ORIGINS`，逗号分隔，不要写 `*`。数据库账号仍只用 `DB_USERNAME`、`DB_PASSWORD`。

## 成员列表

已入团成员可调用 `GET /api/team/members`。返回数组，元素为 `id`（账号 id，创建任务时的 `assigneeId`）、`displayName`、`role`（`CREATOR` 或 `MEMBER`）。未登录 401；已登录未入团 403。

返回 [[00-项目概览/项目首页]]。
