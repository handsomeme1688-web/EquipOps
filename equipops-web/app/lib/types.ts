export type ConnectionMode = "online" | "preview";

export interface CurrentUser {
  userId: number;
  username: string;
  realName: string;
  deptId: number;
  deptName: string;
  permissions: string[];
}

export type DeviceStatus = "正常" | "维修中" | "停用" | "报废";

export interface Device {
  id: number;
  code: string;
  name: string;
  model: string;
  location: string;
  status: DeviceStatus;
  description?: string;
  deptId: number;
  deptName: string;
  ownerId: number;
  ownerName: string;
  createTime: string;
  updateTime: string;
  health?: number;
  runningHours?: number;
  lastInspection?: string;
}

export interface DeviceInput {
  code: string;
  name: string;
  model: string;
  location: string;
  ownerId: number;
  description?: string;
}

export interface DevicePage {
  records: Device[];
  total: number;
  current: number;
  size: number;
  pages: number;
}

export type OrderStatus =
  | "待受理"
  | "已接单"
  | "维修中"
  | "委外中"
  | "待验收"
  | "已完成"
  | "已关闭";

export type Priority = "紧急" | "高" | "中" | "低";

export interface WorkOrder {
  id: number;
  code: string;
  title: string;
  deviceCode: string;
  deviceName: string;
  location: string;
  status: OrderStatus;
  priority: Priority;
  reporter: string;
  assignee?: string;
  createdAt: string;
  elapsed: string;
  description: string;
  slaRisk?: boolean;
  progress: number;
}

export interface ApiResult<T> {
  code: number;
  msg: string;
  data: T;
}
