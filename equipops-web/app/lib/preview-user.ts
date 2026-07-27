import type { CurrentUser } from "./types";

export const previewUser: CurrentUser = {
  userId: 1,
  username: "admin",
  realName: "林屿",
  deptId: 1,
  deptName: "设备运维中心",
  permissions: [
    "device:view",
    "device:create",
    "device:update",
    "device:delete",
    "order:view",
    "order:create",
    "order:accept",
    "order:repair",
    "order:outsource",
    "order:submit",
    "order:audit",
    "order:cancel",
    "system:dept:view",
    "system:dept:manage",
    "system:user:view",
    "system:user:manage",
    "system:role:view",
    "system:role:manage",
  ],
};
