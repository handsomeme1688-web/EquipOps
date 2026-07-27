export type WorkspaceView =
  | "overview"
  | "devices"
  | "orders"
  | "assistant"
  | "admin"
  | "audit"
  | "roadmap";

export type AdminTab = "users" | "depts" | "roles";
export type AuthMode = "login" | "register";

const workspaceRoutes: Record<Exclude<WorkspaceView, "admin">, string> = {
  overview: "/overview",
  devices: "/devices",
  orders: "/work-orders",
  assistant: "/assistant",
  audit: "/audit",
  roadmap: "/roadmap",
};

const adminRoutes: Record<AdminTab, string> = {
  users: "/organization/users",
  depts: "/organization/departments",
  roles: "/organization/roles",
};

export function workspacePath(
  view: WorkspaceView,
  adminTab: AdminTab = "users",
) {
  return view === "admin" ? adminRoutes[adminTab] : workspaceRoutes[view];
}
