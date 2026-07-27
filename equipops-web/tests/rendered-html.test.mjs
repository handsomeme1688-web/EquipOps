import assert from "node:assert/strict";
import { access, readFile } from "node:fs/promises";
import test from "node:test";

const projectRoot = new URL("../", import.meta.url);

async function render(path = "/") {
  const workerUrl = new URL("../dist/server/index.js", import.meta.url);
  workerUrl.searchParams.set("test", `${process.pid}-${Date.now()}`);
  const { default: worker } = await import(workerUrl.href);

  return worker.fetch(
    new Request(`http://localhost${path}`, {
      headers: { accept: "text/html" },
    }),
    {
      ASSETS: {
        fetch: async () => new Response("Not found", { status: 404 }),
      },
    },
    {
      waitUntil() {},
      passThroughOnException() {},
    },
  );
}

test("redirects the root route to the login page", async () => {
  const response = await render("/");
  assert.ok([301, 302, 307, 308].includes(response.status));
  assert.equal(
    new URL(response.headers.get("location"), "http://localhost").pathname,
    "/login",
  );
});

test("server-renders independent authentication routes", async () => {
  const response = await render("/login");
  assert.equal(response.status, 200);
  assert.match(response.headers.get("content-type") ?? "", /^text\/html\b/i);

  const html = await response.text();
  assert.match(html, /<title>EquipOps · 智能设备运维平台<\/title>/i);
  assert.match(html, /EquipOps/);
  assert.match(html, /欢迎回来/);
  assert.doesNotMatch(html, /codex-preview|Your site is taking shape/);

  const registerResponse = await render("/register");
  assert.equal(registerResponse.status, 200);
  assert.match(await registerResponse.text(), /创建账户/);
});

test("server-renders an independently addressable workspace route", async () => {
  const response = await render("/devices");
  assert.equal(response.status, 200);
  assert.match(await response.text(), /正在校验工作台会话/);
});

test("keeps API wiring aligned with the current backend", async () => {
  const [apiSource, pageSource, loginSource, routesSource, packageJson] =
    await Promise.all([
      readFile(new URL("../app/lib/api.ts", import.meta.url), "utf8"),
      readFile(new URL("../app/page.tsx", import.meta.url), "utf8"),
      readFile(
        new URL("../app/components/LoginScreen.tsx", import.meta.url),
        "utf8",
      ),
      readFile(new URL("../app/lib/routes.ts", import.meta.url), "utf8"),
      readFile(new URL("../package.json", import.meta.url), "utf8"),
    ]);

  assert.match(apiSource, /"\/auth\/login"/);
  assert.match(apiSource, /"\/auth\/register"/);
  assert.match(apiSource, /"\/auth\/me"/);
  assert.match(apiSource, /`\/devices\/page\?/);
  assert.match(apiSource, /Authorization/);
  assert.match(pageSource, /redirect\("\/login"\)/);
  assert.match(loginSource, /创建账户/);
  assert.match(loginSource, /发送验证码/);
  assert.match(loginSource, /开发验证码/);
  assert.match(loginSource, /手机号验证通过/);
  assert.match(loginSource, /显示确认密码/);
  assert.match(loginSource, /注册成功，请使用用户名和密码重新登录/);
  assert.match(routesSource, /"\/work-orders"/);
  assert.match(routesSource, /"\/organization\/roles"/);
  assert.match(packageJson, /"name": "equipops-web"/);

  const registerStart = apiSource.indexOf("export async function register");
  const currentUserStart = apiSource.indexOf(
    "export function getCurrentUser",
    registerStart,
  );
  const registerSource = apiSource.slice(registerStart, currentUserStart);
  assert.doesNotMatch(registerSource, /localStorage\.setItem/);

  await assert.rejects(
    access(new URL("../app/_sites-preview", import.meta.url)),
  );
  await access(new URL("../app/components/EquipOpsApp.tsx", import.meta.url));
  await access(new URL("../app/login/page.tsx", import.meta.url));
  await access(new URL("../app/register/page.tsx", import.meta.url));
  await access(new URL("../app/overview/page.tsx", import.meta.url));
  await access(new URL("../app/devices/page.tsx", import.meta.url));
  await access(new URL("../app/work-orders/page.tsx", import.meta.url));
  await access(new URL("../app/assistant/page.tsx", import.meta.url));
  await access(new URL("../app/organization/users/page.tsx", import.meta.url));
  await access(
    new URL("../app/organization/departments/page.tsx", import.meta.url),
  );
  await access(new URL("../app/organization/roles/page.tsx", import.meta.url));
  await access(new URL("../app/audit/page.tsx", import.meta.url));
  await access(new URL("../app/roadmap/page.tsx", import.meta.url));
  await access(new URL("../app/lib/mock-data.ts", import.meta.url));
  await access(projectRoot);
});
