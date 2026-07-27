"use client";

import {
  Activity,
  AlertCircle,
  AlertTriangle,
  ArrowDownRight,
  ArrowRight,
  ArrowUpRight,
  Bell,
  Bot,
  Boxes,
  Building2,
  CalendarClock,
  Check,
  CheckCircle2,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  CircleDot,
  ClipboardCheck,
  Clock3,
  Command,
  Download,
  Ellipsis,
  ExternalLink,
  FileText,
  Filter,
  Gauge,
  LayoutDashboard,
  ListFilter,
  LogOut,
  Menu,
  MessageSquareText,
  MoreHorizontal,
  Network,
  Paperclip,
  Play,
  Plus,
  Radio,
  RefreshCw,
  Search,
  Send,
  Settings,
  ShieldCheck,
  Sparkles,
  SquareActivity,
  Upload,
  Users,
  WandSparkles,
  Wrench,
  X,
  Zap,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import {
  FormEvent,
  ReactNode,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import {
  createDevice as createDeviceRequest,
  getDevicePage,
} from "../lib/api";
import {
  auditRows,
  devices as initialDevices,
  teamUsers,
  workOrders as initialOrders,
} from "../lib/mock-data";
import type { AdminTab, WorkspaceView } from "../lib/routes";
import type {
  ConnectionMode,
  CurrentUser,
  Device,
  DeviceInput,
  DeviceStatus,
  OrderStatus,
  Priority,
  WorkOrder,
} from "../lib/types";

type ModalName = "device" | "order" | null;

interface EquipOpsAppProps {
  user: CurrentUser;
  connectionMode: ConnectionMode;
  view: WorkspaceView;
  adminTab: AdminTab;
  onNavigate: (view: WorkspaceView, adminTab?: AdminTab) => void;
  onSignOut: () => void;
}

interface NavItem {
  id: WorkspaceView;
  label: string;
  icon: LucideIcon;
  badge?: string;
  permission?: string;
}

const navGroups: { label: string; items: NavItem[] }[] = [
  {
    label: "工作台",
    items: [
      { id: "overview", label: "运营总览", icon: LayoutDashboard },
      {
        id: "devices",
        label: "设备台账",
        icon: Boxes,
        permission: "device:view",
      },
      {
        id: "orders",
        label: "维修工单",
        icon: Wrench,
        badge: "3",
        permission: "order:view",
      },
      { id: "assistant", label: "RepairMind", icon: Sparkles },
    ],
  },
  {
    label: "治理",
    items: [
      {
        id: "admin",
        label: "组织与权限",
        icon: Users,
        permission: "system:user:view",
      },
      { id: "audit", label: "审计与监控", icon: ShieldCheck },
      { id: "roadmap", label: "建设进度", icon: CalendarClock },
    ],
  },
];

const viewMeta: Record<WorkspaceView, { eyebrow: string; title: string }> = {
  overview: { eyebrow: "OPERATIONS CENTER", title: "运营总览" },
  devices: { eyebrow: "ASSET REGISTRY", title: "设备台账" },
  orders: { eyebrow: "WORK ORDER FLOW", title: "维修工单" },
  assistant: { eyebrow: "REPAIRMIND AI", title: "维修助手" },
  admin: { eyebrow: "ACCESS CONTROL", title: "组织与权限" },
  audit: { eyebrow: "OBSERVABILITY", title: "审计与监控" },
  roadmap: { eyebrow: "BUILD PROGRESS", title: "建设进度" },
};

const flowSteps: { status: OrderStatus; short: string }[] = [
  { status: "待受理", short: "报修" },
  { status: "已接单", short: "接单" },
  { status: "维修中", short: "维修" },
  { status: "待验收", short: "验收" },
  { status: "已完成", short: "完成" },
];

const phases = [
  {
    code: "P0",
    range: "Day 1–7",
    title: "独立开发地基",
    status: "已完成",
    progress: 100,
    detail: "HTTP、Spring Boot、MyBatis、JWT、测试习惯",
  },
  {
    code: "P1",
    range: "Day 8–21",
    title: "Java 核心服务",
    status: "进行中",
    progress: 36,
    detail: "设备 CRUD、RBAC、数据隔离、文件安全",
  },
  {
    code: "P1B",
    range: "Day 22–25",
    title: "企业工程加固",
    status: "未开始",
    progress: 0,
    detail: "Token 生命周期、测试门禁、可观测性、MQ",
  },
  {
    code: "P2",
    range: "Day 26–35",
    title: "RepairMind AI",
    status: "未开始",
    progress: 0,
    detail: "FastAPI、混合检索、RAG、受控工具调用",
  },
  {
    code: "P3/4",
    range: "Day 36–51",
    title: "深挖与交付",
    status: "未开始",
    progress: 0,
    detail: "性能证据、系统设计、面试与最终验收",
  },
];

const orderColumns: { key: string; title: string; states: OrderStatus[] }[] = [
  { key: "queue", title: "待响应", states: ["待受理"] },
  { key: "active", title: "处理中", states: ["已接单", "维修中", "委外中"] },
  { key: "verify", title: "待验收", states: ["待验收"] },
  { key: "done", title: "近期完成", states: ["已完成", "已关闭"] },
];

const priorityClass: Record<Priority, string> = {
  紧急: "priority-critical",
  高: "priority-high",
  中: "priority-medium",
  低: "priority-low",
};

const aiStarterMessages = [
  {
    role: "assistant" as const,
    text: "你好，我是 RepairMind。我可以结合设备手册、维修案例和你有权限访问的设备数据，协助定位故障。涉及安全参数时，我会强制附上来源。",
    time: "刚刚",
  },
];

export function EquipOpsApp({
  user,
  connectionMode,
  view,
  adminTab,
  onNavigate,
  onSignOut,
}: EquipOpsAppProps) {
  const [collapsed, setCollapsed] = useState(false);
  const [mobileMenu, setMobileMenu] = useState(false);
  const [notificationsOpen, setNotificationsOpen] = useState(false);
  const [commandOpen, setCommandOpen] = useState(false);
  const [modal, setModal] = useState<ModalName>(null);
  const [selectedDevice, setSelectedDevice] = useState<Device | null>(null);
  const [selectedOrder, setSelectedOrder] = useState<WorkOrder | null>(null);
  const [deviceRows, setDeviceRows] = useState<Device[]>(initialDevices);
  const [orders, setOrders] = useState<WorkOrder[]>(initialOrders);
  const [deviceSearch, setDeviceSearch] = useState("");
  const [deviceStatus, setDeviceStatus] = useState<"全部" | DeviceStatus>("全部");
  const [loadingDevices, setLoadingDevices] = useState(
    connectionMode === "online",
  );
  const [toast, setToast] = useState("");
  const [aiMessages, setAiMessages] =
    useState<AiMessage[]>(aiStarterMessages);
  const [aiInput, setAiInput] = useState("");
  const [aiThinking, setAiThinking] = useState(false);
  const toastTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const permissionSet = useMemo(
    () => new Set(user.permissions || []),
    [user.permissions],
  );
  const can = (permission?: string) =>
    !permission || permissionSet.has(permission);

  const visibleNav = navGroups.map((group) => ({
    ...group,
    items: group.items.filter((item) => can(item.permission)),
  }));

  useEffect(() => {
    function handleShortcut(event: KeyboardEvent) {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        setCommandOpen((open) => !open);
      }
      if (event.key === "Escape") {
        setCommandOpen(false);
        setModal(null);
        setSelectedDevice(null);
        setSelectedOrder(null);
        setNotificationsOpen(false);
      }
    }
    window.addEventListener("keydown", handleShortcut);
    return () => window.removeEventListener("keydown", handleShortcut);
  }, []);

  useEffect(() => {
    if (connectionMode !== "online") return;
    let active = true;
    getDevicePage({ pageNum: 1, pageSize: 50 })
      .then((page) => {
        if (active) setDeviceRows(page.records);
      })
      .catch(() => {
        if (active) notify("设备接口暂不可用，已保留本地预览数据");
      })
      .finally(() => {
        if (active) setLoadingDevices(false);
      });
    return () => {
      active = false;
    };
  }, [connectionMode]);

  function notify(message: string) {
    setToast(message);
    if (toastTimer.current) clearTimeout(toastTimer.current);
    toastTimer.current = setTimeout(() => setToast(""), 2800);
  }

  function navigate(nextView: WorkspaceView, nextAdminTab?: AdminTab) {
    onNavigate(nextView, nextAdminTab);
    setMobileMenu(false);
    setCommandOpen(false);
  }

  const filteredDevices = deviceRows.filter((device) => {
    const keyword = deviceSearch.trim().toLowerCase();
    const matchesKeyword =
      !keyword ||
      device.name.toLowerCase().includes(keyword) ||
      device.code.toLowerCase().includes(keyword) ||
      device.location.toLowerCase().includes(keyword);
    return (
      matchesKeyword &&
      (deviceStatus === "全部" || device.status === deviceStatus)
    );
  });

  async function addDevice(input: DeviceInput) {
    if (connectionMode === "online") {
      try {
        const created = await createDeviceRequest(input);
        setDeviceRows((rows) => [created, ...rows]);
        notify("设备已写入 EquipOps 后端");
      } catch (error) {
        notify(error instanceof Error ? error.message : "创建设备失败");
        return false;
      }
    } else {
      const created: Device = {
        ...input,
        id: Math.max(...deviceRows.map((item) => item.id)) + 1,
        status: "正常",
        deptId: user.deptId,
        deptName: user.deptName,
        ownerName: user.realName,
        createTime: "刚刚",
        updateTime: "刚刚",
        health: 100,
        runningHours: 0,
        lastInspection: "待首次点检",
      };
      setDeviceRows((rows) => [created, ...rows]);
      notify("已在预览数据中创建设备");
    }
    setModal(null);
    return true;
  }

  function createOrder(input: {
    deviceCode: string;
    title: string;
    priority: Priority;
    description: string;
  }) {
    const device = deviceRows.find((item) => item.code === input.deviceCode);
    const order: WorkOrder = {
      id: Math.max(...orders.map((item) => item.id)) + 1,
      code: `WO-20260726-${String(orders.length + 52).padStart(3, "0")}`,
      title: input.title,
      deviceCode: input.deviceCode,
      deviceName: device?.name || "未知设备",
      location: device?.location || "位置待确认",
      status: "待受理",
      priority: input.priority,
      reporter: user.realName,
      createdAt: "刚刚",
      elapsed: "0分钟",
      description: input.description,
      progress: 8,
    };
    setOrders((rows) => [order, ...rows]);
    setModal(null);
    notify(
      connectionMode === "online"
        ? "工单接口将在 Day 15 接通，当前已保存到预览层"
        : "报修工单已创建",
    );
  }

  function advanceOrder(order: WorkOrder) {
    const nextStatus: Partial<Record<OrderStatus, OrderStatus>> = {
      待受理: "已接单",
      已接单: "维修中",
      维修中: "待验收",
      委外中: "待验收",
      待验收: "已完成",
    };
    const next = nextStatus[order.status];
    if (!next) return;
    const updated = {
      ...order,
      status: next,
      assignee: order.assignee || user.realName,
      progress:
        next === "已接单"
          ? 28
          : next === "维修中"
            ? 55
            : next === "待验收"
              ? 88
              : 100,
    };
    setOrders((rows) =>
      rows.map((item) => (item.id === order.id ? updated : item)),
    );
    setSelectedOrder(updated);
    notify(`工单已流转至「${next}」`);
  }

  function handleAiSubmit(event: FormEvent) {
    event.preventDefault();
    const question = aiInput.trim();
    if (!question || aiThinking) return;
    setAiMessages((messages) => [
      ...messages,
      { role: "user", text: question, time: "刚刚" },
    ]);
    setAiInput("");
    setAiThinking(true);
    window.setTimeout(() => {
      setAiMessages((messages) => [
        ...messages,
        {
          role: "assistant",
          text: "从现象看，优先排查主轴轴承预紧状态与刀具动平衡。建议先在空载条件下分段升速，记录 4,000 / 6,000 / 8,000 rpm 三个点的振动值；若只在特定转速区间突增，应暂停继续升速，并由维修工程师检查主轴组件。不要在防护门打开时运行主轴。",
          time: "刚刚",
          citations: [
            "DMU 50 维护手册 · 7.3 主轴振动诊断",
            "案例 KB-042 · 高速段周期性异响",
          ],
        },
      ]);
      setAiThinking(false);
    }, 900);
  }

  return (
    <div
      className={`app-shell ${collapsed ? "sidebar-collapsed" : ""} ${
        mobileMenu ? "mobile-menu-open" : ""
      }`}
    >
      <aside className="sidebar">
        <div className="sidebar-top">
          <button
            className="brand-lockup"
            onClick={() => navigate("overview")}
            aria-label="返回运营总览"
          >
            <span className="brand-glyph">
              <Gauge size={21} strokeWidth={2.2} />
            </span>
            <span className="brand-words">
              <strong>EquipOps</strong>
              <small>智能设备运维平台</small>
            </span>
          </button>
          <button
            className="collapse-button"
            onClick={() => setCollapsed((value) => !value)}
            aria-label={collapsed ? "展开导航" : "收起导航"}
          >
            <ChevronLeft size={17} />
          </button>
        </div>

        <nav className="sidebar-nav" aria-label="主要导航">
          {visibleNav.map((group) => (
            <div className="nav-group" key={group.label}>
              <p>{group.label}</p>
              {group.items.map((item) => {
                const Icon = item.icon;
                return (
                  <button
                    key={item.id}
                    className={`nav-item ${view === item.id ? "active" : ""}`}
                    onClick={() => navigate(item.id)}
                    title={collapsed ? item.label : undefined}
                  >
                    <Icon size={18} />
                    <span>{item.label}</span>
                    {item.badge && <em>{item.badge}</em>}
                  </button>
                );
              })}
            </div>
          ))}
        </nav>

        <div className="sidebar-bottom">
          <div className="connection-card">
            <span
              className={`connection-icon ${
                connectionMode === "online" ? "online" : ""
              }`}
            >
              <Radio size={16} />
            </span>
            <span>
              <strong>
                {connectionMode === "online" ? "后端已连接" : "完整预览模式"}
              </strong>
              <small>
                {connectionMode === "online"
                  ? "实时读取 EquipOps API"
                  : "后端可按计划逐步接通"}
              </small>
            </span>
          </div>
          <button className="sidebar-user" onClick={onSignOut}>
            <span className="avatar">{user.realName.slice(0, 1)}</span>
            <span>
              <strong>{user.realName}</strong>
              <small>{user.deptName}</small>
            </span>
            <LogOut size={16} />
          </button>
        </div>
      </aside>

      {mobileMenu && (
        <button
          className="sidebar-backdrop"
          onClick={() => setMobileMenu(false)}
          aria-label="关闭导航"
        />
      )}

      <main className="workspace">
        <header className="topbar">
          <div className="topbar-title">
            <button
              className="mobile-menu-button"
              onClick={() => setMobileMenu(true)}
              aria-label="打开导航"
            >
              <Menu size={20} />
            </button>
            <span>{viewMeta[view].eyebrow}</span>
            <strong>{viewMeta[view].title}</strong>
          </div>

          <div className="topbar-actions">
            <button className="command-trigger" onClick={() => setCommandOpen(true)}>
              <Search size={16} />
              <span>搜索设备、工单或命令</span>
              <kbd>
                <Command size={12} /> K
              </kbd>
            </button>
            <div className="topbar-divider" />
            <button
              className="icon-button notification-button"
              onClick={() => setNotificationsOpen((value) => !value)}
              aria-label="查看通知"
            >
              <Bell size={19} />
              <span />
            </button>
            <button className="profile-chip" onClick={() => navigate("admin")}>
              <span className="avatar">{user.realName.slice(0, 1)}</span>
              <span>
                <strong>{user.realName}</strong>
                <small>{user.deptName}</small>
              </span>
              <ChevronDown size={15} />
            </button>
          </div>

          {notificationsOpen && (
            <NotificationPanel onClose={() => setNotificationsOpen(false)} />
          )}
        </header>

        <div className="content-stage">
          {view === "overview" && (
            <Overview
              user={user}
              orders={orders}
              devices={deviceRows}
              onNavigate={navigate}
              onSelectOrder={setSelectedOrder}
            />
          )}
          {view === "devices" && (
            <DevicesView
              rows={filteredDevices}
              total={deviceRows.length}
              search={deviceSearch}
              status={deviceStatus}
              loading={loadingDevices}
              canCreate={can("device:create")}
              onSearch={setDeviceSearch}
              onStatus={setDeviceStatus}
              onCreate={() => setModal("device")}
              onSelect={setSelectedDevice}
            />
          )}
          {view === "orders" && (
            <OrdersView
              orders={orders}
              canCreate={can("order:create")}
              onCreate={() => setModal("order")}
              onSelect={setSelectedOrder}
            />
          )}
          {view === "assistant" && (
            <AssistantView
              messages={aiMessages}
              input={aiInput}
              thinking={aiThinking}
              onInput={setAiInput}
              onSubmit={handleAiSubmit}
              onPrompt={(prompt) => setAiInput(prompt)}
            />
          )}
          {view === "admin" && (
            <AdminView
              tab={adminTab}
              onTab={(nextTab) => navigate("admin", nextTab)}
              user={user}
              canManage={can("system:user:manage")}
              onNotify={notify}
            />
          )}
          {view === "audit" && <AuditView />}
          {view === "roadmap" && <RoadmapView />}
        </div>
      </main>

      {modal === "device" && (
        <DeviceModal
          onClose={() => setModal(null)}
          onSubmit={addDevice}
        />
      )}
      {modal === "order" && (
        <OrderModal
          devices={deviceRows}
          onClose={() => setModal(null)}
          onSubmit={createOrder}
        />
      )}
      {selectedDevice && (
        <DeviceDrawer
          device={selectedDevice}
          canEdit={can("device:update")}
          onClose={() => setSelectedDevice(null)}
          onReport={() => {
            setSelectedDevice(null);
            setModal("order");
          }}
          onNotify={notify}
        />
      )}
      {selectedOrder && (
        <OrderDrawer
          order={selectedOrder}
          canAdvance={
            can("order:accept") ||
            can("order:repair") ||
            can("order:submit") ||
            can("order:audit")
          }
          onAdvance={() => advanceOrder(selectedOrder)}
          onClose={() => setSelectedOrder(null)}
        />
      )}
      {commandOpen && (
        <CommandPalette
          navGroups={visibleNav}
          onNavigate={navigate}
          onClose={() => setCommandOpen(false)}
          onDevice={(device) => {
            setSelectedDevice(device);
            setCommandOpen(false);
          }}
          devices={deviceRows}
        />
      )}
      {toast && (
        <div className="toast" role="status">
          <CheckCircle2 size={17} />
          {toast}
        </div>
      )}
    </div>
  );
}

function PageHeading({
  eyebrow,
  title,
  description,
  actions,
}: {
  eyebrow: string;
  title: string;
  description: string;
  actions?: ReactNode;
}) {
  return (
    <div className="page-heading">
      <div>
        <span className="section-eyebrow">{eyebrow}</span>
        <h1>{title}</h1>
        <p>{description}</p>
      </div>
      {actions && <div className="page-actions">{actions}</div>}
    </div>
  );
}

function Overview({
  user,
  orders,
  devices,
  onNavigate,
  onSelectOrder,
}: {
  user: CurrentUser;
  orders: WorkOrder[];
  devices: Device[];
  onNavigate: (view: WorkspaceView) => void;
  onSelectOrder: (order: WorkOrder) => void;
}) {
  const normalCount = devices.filter((item) => item.status === "正常").length;
  const activeOrders = orders.filter(
    (item) => !["已完成", "已关闭"].includes(item.status),
  );

  return (
    <div className="view-stack overview-view">
      <PageHeading
        eyebrow="2026年7月26日 · 周日"
        title={`上午好，${user.realName}`}
        description="这里是今天的设备运行快照与待处理事项。"
        actions={
          <>
            <button className="secondary-button">
              <Download size={16} />
              导出日报
            </button>
            <button className="primary-button" onClick={() => onNavigate("orders")}>
              <Plus size={17} />
              发起报修
            </button>
          </>
        }
      />

      <section className="kpi-grid" aria-label="关键指标">
        <MetricCard
          icon={Boxes}
          label="设备总数"
          value={String(devices.length)}
          suffix="台"
          change="+6.2%"
          trend="up"
          note="本月新增 4 台"
        />
        <MetricCard
          icon={SquareActivity}
          label="设备健康率"
          value={`${Math.round((normalCount / Math.max(devices.length, 1)) * 100)}`}
          suffix="%"
          change="+1.8%"
          trend="up"
          note={`${normalCount} 台运行正常`}
          accent
        />
        <MetricCard
          icon={Wrench}
          label="处理中工单"
          value={String(activeOrders.length)}
          suffix="单"
          change="2 单"
          trend="neutral"
          note="存在 SLA 风险"
        />
        <MetricCard
          icon={Clock3}
          label="平均修复时长"
          value="3.7"
          suffix="小时"
          change="-12.4%"
          trend="down"
          note="较上月缩短 31 分钟"
        />
      </section>

      <section className="overview-grid">
        <div className="panel health-panel">
          <div className="panel-heading">
            <div>
              <span>设备健康</span>
              <h2>运行状态分布</h2>
            </div>
            <button className="text-button" onClick={() => onNavigate("devices")}>
              查看台账 <ArrowRight size={15} />
            </button>
          </div>
          <div className="health-content">
            <div
              className="health-donut"
              style={{
                background: `conic-gradient(#35d08a 0 72%, #ffb657 72% 86%, #667085 86% 100%)`,
              }}
            >
              <div>
                <strong>92.6%</strong>
                <span>整体健康</span>
              </div>
            </div>
            <div className="health-legend">
              <div>
                <span className="legend-dot healthy" />
                <p>
                  <strong>运行正常</strong>
                  <small>关键参数稳定</small>
                </p>
                <em>{normalCount}</em>
              </div>
              <div>
                <span className="legend-dot warning" />
                <p>
                  <strong>需要关注</strong>
                  <small>已有告警或工单</small>
                </p>
                <em>2</em>
              </div>
              <div>
                <span className="legend-dot offline" />
                <p>
                  <strong>停用 / 报废</strong>
                  <small>不计入运行产能</small>
                </p>
                <em>1</em>
              </div>
            </div>
          </div>
        </div>

        <div className="panel throughput-panel">
          <div className="panel-heading">
            <div>
              <span>维修效率</span>
              <h2>近 7 日工单吞吐</h2>
            </div>
            <span className="positive-chip">
              <ArrowUpRight size={14} /> 18.7%
            </span>
          </div>
          <div className="bar-chart" aria-label="近七日工单完成量">
            {[42, 65, 48, 78, 56, 88, 72].map((height, index) => (
              <div className="bar-column" key={height + index}>
                <div className="bar-track">
                  <span
                    className={index === 5 ? "highlight" : ""}
                    style={{ height: `${height}%` }}
                  />
                </div>
                <small>{["一", "二", "三", "四", "五", "六", "日"][index]}</small>
              </div>
            ))}
          </div>
          <div className="chart-footer">
            <p>
              <strong>31</strong>
              <span>本周完成</span>
            </p>
            <p>
              <strong>4.4</strong>
              <span>日均完成</span>
            </p>
            <p>
              <strong>96%</strong>
              <span>SLA 达成</span>
            </p>
          </div>
        </div>

        <div className="panel order-focus-panel">
          <div className="panel-heading">
            <div>
              <span>现场焦点</span>
              <h2>优先处理</h2>
            </div>
            <button className="icon-button">
              <MoreHorizontal size={19} />
            </button>
          </div>
          <div className="focus-list">
            {activeOrders.slice(0, 3).map((order) => (
              <button
                className="focus-row"
                key={order.id}
                onClick={() => onSelectOrder(order)}
              >
                <span className={`priority-rail ${priorityClass[order.priority]}`} />
                <span className="focus-main">
                  <span>
                    <em>{order.code}</em>
                    <StatusBadge value={order.status} />
                  </span>
                  <strong>{order.title}</strong>
                  <small>
                    {order.deviceName} · {order.location}
                  </small>
                </span>
                <span className="focus-time">
                  <strong>{order.elapsed}</strong>
                  <small>{order.slaRisk ? "SLA 风险" : order.assignee || "待指派"}</small>
                </span>
                <ChevronRight size={17} />
              </button>
            ))}
          </div>
        </div>

        <div className="panel ai-insight-panel">
          <div className="ai-orbit">
            <Bot size={23} />
          </div>
          <span className="ai-label">REPAIRMIND INSIGHT</span>
          <h2>主轴振动类故障本月出现 3 次</h2>
          <p>
            三次均集中在 8,000 rpm
            以上高速段。建议检查刀具动平衡记录，并安排主轴轴承趋势检测。
          </p>
          <div className="insight-source">
            <FileText size={15} />
            基于 12 条维修记录与 2 份设备手册
          </div>
          <button onClick={() => onNavigate("assistant")}>
            继续分析 <ArrowRight size={16} />
          </button>
        </div>
      </section>

      <section className="panel flow-panel">
        <div className="panel-heading">
          <div>
            <span>工单状态机</span>
            <h2>今日流转概览</h2>
          </div>
          <span className="live-indicator">
            <span /> 实时更新
          </span>
        </div>
        <div className="flow-track">
          {flowSteps.map((step, index) => (
            <div className="flow-step" key={step.status}>
              <span className={index < 3 ? "active" : ""}>
                {index < 2 ? <Check size={15} /> : index + 1}
              </span>
              <div>
                <strong>{step.short}</strong>
                <small>
                  {orders.filter((order) => order.status === step.status).length} 单
                </small>
              </div>
              {index < flowSteps.length - 1 && <i />}
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}

function MetricCard({
  icon: Icon,
  label,
  value,
  suffix,
  change,
  trend,
  note,
  accent = false,
}: {
  icon: LucideIcon;
  label: string;
  value: string;
  suffix: string;
  change: string;
  trend: "up" | "down" | "neutral";
  note: string;
  accent?: boolean;
}) {
  return (
    <div className={`metric-card ${accent ? "accent" : ""}`}>
      <div className="metric-top">
        <span className="metric-icon">
          <Icon size={19} />
        </span>
        <span className={`trend-chip ${trend}`}>
          {trend === "up" && <ArrowUpRight size={13} />}
          {trend === "down" && <ArrowDownRight size={13} />}
          {trend === "neutral" && <AlertCircle size={13} />}
          {change}
        </span>
      </div>
      <p>{label}</p>
      <div className="metric-value">
        <strong>{value}</strong>
        <span>{suffix}</span>
      </div>
      <small>{note}</small>
    </div>
  );
}

function DevicesView({
  rows,
  total,
  search,
  status,
  loading,
  canCreate,
  onSearch,
  onStatus,
  onCreate,
  onSelect,
}: {
  rows: Device[];
  total: number;
  search: string;
  status: "全部" | DeviceStatus;
  loading: boolean;
  canCreate: boolean;
  onSearch: (value: string) => void;
  onStatus: (value: "全部" | DeviceStatus) => void;
  onCreate: () => void;
  onSelect: (device: Device) => void;
}) {
  return (
    <div className="view-stack">
      <PageHeading
        eyebrow={`${total} ASSETS REGISTERED`}
        title="设备台账"
        description="集中管理设备档案、运行状态、责任人与安全附件。"
        actions={
          canCreate && (
            <button className="primary-button" onClick={onCreate}>
              <Plus size={17} /> 新建设备
            </button>
          )
        }
      />

      <section className="filter-panel">
        <label className="search-field">
          <Search size={17} />
          <input
            placeholder="搜索设备名称、编号或位置"
            value={search}
            onChange={(event) => onSearch(event.target.value)}
          />
          {search && (
            <button onClick={() => onSearch("")} aria-label="清除搜索">
              <X size={15} />
            </button>
          )}
        </label>
        <div className="segmented-control">
          {(["全部", "正常", "维修中", "停用", "报废"] as const).map((item) => (
            <button
              key={item}
              className={status === item ? "active" : ""}
              onClick={() => onStatus(item)}
            >
              {item}
            </button>
          ))}
        </div>
        <button className="secondary-button filter-button">
          <ListFilter size={16} />
          更多筛选
        </button>
      </section>

      <section className="panel table-panel">
        <div className="table-summary">
          <span>
            {loading ? "正在同步后端设备..." : `共 ${rows.length} 条结果`}
          </span>
          <div>
            <button className="icon-button" aria-label="刷新">
              <RefreshCw size={16} />
            </button>
            <button className="icon-button" aria-label="更多操作">
              <Ellipsis size={17} />
            </button>
          </div>
        </div>
        <div className="data-table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>设备</th>
                <th>状态</th>
                <th>归属 / 位置</th>
                <th>责任人</th>
                <th>健康度</th>
                <th>更新时间</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {rows.map((device) => (
                <tr key={device.id} onClick={() => onSelect(device)}>
                  <td>
                    <div className="asset-cell">
                      <span className="asset-icon">
                        <Boxes size={18} />
                      </span>
                      <span>
                        <strong>{device.name}</strong>
                        <small>
                          {device.code} · {device.model}
                        </small>
                      </span>
                    </div>
                  </td>
                  <td>
                    <StatusBadge value={device.status} />
                  </td>
                  <td>
                    <strong className="table-primary">{device.deptName}</strong>
                    <small className="table-secondary">{device.location}</small>
                  </td>
                  <td>
                    <span className="owner-chip">
                      <span>{device.ownerName.slice(0, 1)}</span>
                      {device.ownerName}
                    </span>
                  </td>
                  <td>
                    <div className="health-meter">
                      <span>
                        <i
                          style={{
                            width: `${device.health ?? (device.status === "正常" ? 92 : 64)}%`,
                          }}
                        />
                      </span>
                      <strong>
                        {device.health ?? (device.status === "正常" ? 92 : 64)}
                      </strong>
                    </div>
                  </td>
                  <td>
                    <span className="muted-cell">{device.updateTime}</span>
                  </td>
                  <td>
                    <button className="icon-button" aria-label="打开设备详情">
                      <ChevronRight size={17} />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {rows.length === 0 && (
          <div className="empty-state">
            <Search size={23} />
            <strong>没有匹配的设备</strong>
            <span>换一个关键词或清除筛选条件。</span>
          </div>
        )}
        <div className="pagination">
          <span>每页 10 条</span>
          <div>
            <button disabled>
              <ChevronLeft size={15} />
            </button>
            <button className="active">1</button>
            <button>2</button>
            <button>
              <ChevronRight size={15} />
            </button>
          </div>
        </div>
      </section>
    </div>
  );
}

function OrdersView({
  orders,
  canCreate,
  onCreate,
  onSelect,
}: {
  orders: WorkOrder[];
  canCreate: boolean;
  onCreate: () => void;
  onSelect: (order: WorkOrder) => void;
}) {
  return (
    <div className="view-stack">
      <PageHeading
        eyebrow="7-STATE CONTROLLED WORKFLOW"
        title="维修工单"
        description="以受控状态机推进报修、承接、维修、委外与验收。"
        actions={
          <>
            <button className="secondary-button">
              <Filter size={16} /> 筛选
            </button>
            {canCreate && (
              <button className="primary-button" onClick={onCreate}>
                <Plus size={17} /> 发起报修
              </button>
            )}
          </>
        }
      />

      <section className="order-toolbar">
        <div className="order-summary-pills">
          <span>
            <i className="critical" /> 2 单存在 SLA 风险
          </span>
          <span>
            <i className="active" /> 4 单处理中
          </span>
          <span>
            <i className="success" /> 今日完成 6 单
          </span>
        </div>
        <div className="view-switch">
          <button className="active">看板</button>
          <button>列表</button>
        </div>
      </section>

      <section className="kanban-board">
        {orderColumns.map((column) => {
          const columnOrders = orders.filter((order) =>
            column.states.includes(order.status),
          );
          return (
            <div className="kanban-column" key={column.key}>
              <div className="kanban-heading">
                <span>
                  <i className={column.key} />
                  <strong>{column.title}</strong>
                  <em>{columnOrders.length}</em>
                </span>
                <button className="icon-button">
                  <MoreHorizontal size={17} />
                </button>
              </div>
              <div className="kanban-stack">
                {columnOrders.map((order) => (
                  <button
                    className={`order-card ${
                      order.slaRisk ? "sla-risk" : ""
                    }`}
                    key={order.id}
                    onClick={() => onSelect(order)}
                  >
                    <div className="order-card-top">
                      <span className={priorityClass[order.priority]}>
                        {order.priority}
                      </span>
                      <small>{order.code}</small>
                    </div>
                    <h3>{order.title}</h3>
                    <p>
                      {order.deviceName}
                      <span>·</span>
                      {order.deviceCode}
                    </p>
                    <div className="order-location">
                      <Network size={14} />
                      {order.location}
                    </div>
                    <div className="progress-track">
                      <span style={{ width: `${order.progress}%` }} />
                    </div>
                    <div className="order-card-footer">
                      <span className="mini-avatar">
                        {(order.assignee || order.reporter).slice(0, 1)}
                      </span>
                      <span>{order.assignee || "待指派"}</span>
                      <span className={order.slaRisk ? "risk-time" : ""}>
                        <Clock3 size={13} />
                        {order.elapsed}
                      </span>
                    </div>
                  </button>
                ))}
                {columnOrders.length === 0 && (
                  <div className="kanban-empty">
                    <CheckCircle2 size={18} />
                    暂无工单
                  </div>
                )}
              </div>
            </div>
          );
        })}
      </section>

      <section className="state-machine-note">
        <span className="note-icon">
          <ShieldCheck size={18} />
        </span>
        <div>
          <strong>服务端三层校验</strong>
          <p>
            每次流转都会校验权限码、当前状态与身份关系。前端按钮隐藏仅用于体验优化，不承担授权职责。
          </p>
        </div>
        <button>
          查看状态图 <ExternalLink size={14} />
        </button>
      </section>
    </div>
  );
}

interface AiMessage {
  role: "assistant" | "user";
  text: string;
  time: string;
  citations?: string[];
}

function AssistantView({
  messages,
  input,
  thinking,
  onInput,
  onSubmit,
  onPrompt,
}: {
  messages: AiMessage[];
  input: string;
  thinking: boolean;
  onInput: (value: string) => void;
  onSubmit: (event: FormEvent) => void;
  onPrompt: (value: string) => void;
}) {
  return (
    <div className="assistant-layout">
      <aside className="assistant-sidebar">
        <div className="assistant-title">
          <span className="ai-orbit">
            <Bot size={20} />
          </span>
          <div>
            <strong>RepairMind</strong>
            <small>
              <span /> 知识库就绪
            </small>
          </div>
          <button className="icon-button">
            <Plus size={17} />
          </button>
        </div>
        <div className="conversation-list">
          <p>今天</p>
          <button className="active">
            <MessageSquareText size={16} />
            <span>
              <strong>五轴加工中心异响排查</strong>
              <small>刚刚</small>
            </span>
          </button>
          <button>
            <MessageSquareText size={16} />
            <span>
              <strong>空压机油分压差告警</strong>
              <small>09:23</small>
            </span>
          </button>
          <p>过去 7 天</p>
          <button>
            <MessageSquareText size={16} />
            <span>
              <strong>激光器冷却水温波动</strong>
              <small>周五</small>
            </span>
          </button>
        </div>
        <div className="knowledge-status">
          <div>
            <FileText size={16} />
            <span>
              <strong>知识库</strong>
              <small>148 份文档 · 2,416 片段</small>
            </span>
          </div>
          <span className="sync-chip">已同步</span>
        </div>
      </aside>

      <section className="chat-workspace">
        <header className="chat-header">
          <div>
            <span>当前会话</span>
            <strong>五轴加工中心异响排查</strong>
          </div>
          <div>
            <span className="safe-chip">
              <ShieldCheck size={14} /> 安全模式
            </span>
            <button className="icon-button">
              <MoreHorizontal size={18} />
            </button>
          </div>
        </header>

        <div className="chat-scroll">
          <div className="chat-date">今天</div>
          {messages.map((message, index) => (
            <div
              className={`chat-message ${message.role}`}
              key={`${message.time}-${index}`}
            >
              <span className="message-avatar">
                {message.role === "assistant" ? (
                  <Sparkles size={17} />
                ) : (
                  "林"
                )}
              </span>
              <div>
                <div className="message-meta">
                  <strong>
                    {message.role === "assistant" ? "RepairMind" : "你"}
                  </strong>
                  <span>{message.time}</span>
                </div>
                <div className="message-bubble">
                  <p>{message.text}</p>
                  {message.citations && (
                    <>
                      <div className="safety-callout">
                        <AlertTriangle size={16} />
                        涉及高速旋转设备。开始检查前请执行停机、断电与挂牌上锁。
                      </div>
                      <div className="citation-list">
                        <span>引用来源</span>
                        {message.citations.map((citation, citationIndex) => (
                          <button key={citation}>
                            <FileText size={15} />
                            <span>
                              <strong>{citation}</strong>
                              <small>相关度 {citationIndex === 0 ? "94%" : "87%"}</small>
                            </span>
                            <ExternalLink size={13} />
                          </button>
                        ))}
                      </div>
                      <div className="tool-result">
                        <span>
                          <WandSparkles size={16} />
                          已受控查询设备信息
                        </span>
                        <p>EQ-CNC-0017 · 当前状态：维修中 · 近 90 天相关工单：2</p>
                      </div>
                    </>
                  )}
                </div>
              </div>
            </div>
          ))}
          {thinking && (
            <div className="chat-message assistant">
              <span className="message-avatar">
                <Sparkles size={17} />
              </span>
              <div className="thinking-dots" aria-label="RepairMind 正在检索">
                <span />
                <span />
                <span />
                <em>正在检索手册与历史案例</em>
              </div>
            </div>
          )}
        </div>

        <footer className="chat-composer-wrap">
          <div className="prompt-chips">
            {[
              "列出安全排查步骤",
              "查询这台设备的维修历史",
              "帮我生成报修草稿",
            ].map((prompt) => (
              <button key={prompt} onClick={() => onPrompt(prompt)}>
                {prompt}
              </button>
            ))}
          </div>
          <form className="chat-composer" onSubmit={onSubmit}>
            <button type="button" className="icon-button" aria-label="添加附件">
              <Paperclip size={18} />
            </button>
            <textarea
              rows={1}
              value={input}
              onChange={(event) => onInput(event.target.value)}
              placeholder="描述故障现象，或输入设备编号..."
              onKeyDown={(event) => {
                if (event.key === "Enter" && !event.shiftKey) {
                  event.preventDefault();
                  event.currentTarget.form?.requestSubmit();
                }
              }}
            />
            <button
              className="send-button"
              disabled={!input.trim() || thinking}
              aria-label="发送问题"
            >
              <Send size={17} />
            </button>
          </form>
          <p>
            AI 可能会出错。关键参数请核对引用来源，危险操作必须由合格人员执行。
          </p>
        </footer>
      </section>

      <aside className="assistant-context">
        <div className="context-heading">
          <span>当前上下文</span>
          <button className="icon-button">
            <X size={16} />
          </button>
        </div>
        <div className="context-device">
          <span className="asset-icon">
            <Boxes size={19} />
          </span>
          <div>
            <small>正在分析</small>
            <strong>五轴加工中心</strong>
            <span>EQ-CNC-0017</span>
          </div>
          <StatusBadge value="维修中" />
        </div>
        <dl className="context-facts">
          <div>
            <dt>设备型号</dt>
            <dd>DMU 50</dd>
          </div>
          <div>
            <dt>安装位置</dt>
            <dd>第二生产车间 · A03</dd>
          </div>
          <div>
            <dt>责任人</dt>
            <dd>周八</dd>
          </div>
          <div>
            <dt>累计运行</dt>
            <dd>12,840 h</dd>
          </div>
        </dl>
        <div className="context-divider" />
        <div className="related-orders">
          <div>
            <span>相关工单</span>
            <em>2</em>
          </div>
          <button>
            <span className="status-mini warning" />
            <span>
              <strong>主轴高速段异响</strong>
              <small>WO-20260726-051 · 维修中</small>
            </span>
          </button>
          <button>
            <span className="status-mini success" />
            <span>
              <strong>刀库换刀位置偏差</strong>
              <small>WO-20260512-018 · 已完成</small>
            </span>
          </button>
        </div>
        <div className="context-action-card">
          <ClipboardCheck size={19} />
          <strong>生成报修草稿</strong>
          <p>RepairMind 可以整理当前对话，但落库前必须由你显式确认。</p>
          <button>生成待确认草稿</button>
        </div>
      </aside>
    </div>
  );
}

function AdminView({
  tab,
  onTab,
  user,
  canManage,
  onNotify,
}: {
  tab: "users" | "depts" | "roles";
  onTab: (tab: "users" | "depts" | "roles") => void;
  user: CurrentUser;
  canManage: boolean;
  onNotify: (message: string) => void;
}) {
  return (
    <div className="view-stack">
      <PageHeading
        eyebrow="RBAC · MULTI-ROLE UNION"
        title="组织与权限"
        description="集中维护部门、账户与角色权限。前端呈现权限，服务端做最终判定。"
        actions={
          canManage && (
            <button
              className="primary-button"
              onClick={() => onNotify("管理表单将在对应后端接口完成后自动接通")}
            >
              <Plus size={17} /> 新增成员
            </button>
          )
        }
      />

      <section className="admin-layout">
        <div className="admin-tabs">
          <button
            className={tab === "users" ? "active" : ""}
            onClick={() => onTab("users")}
          >
            <Users size={17} /> 账户
            <span>8</span>
          </button>
          <button
            className={tab === "depts" ? "active" : ""}
            onClick={() => onTab("depts")}
          >
            <Building2 size={17} /> 部门
            <span>4</span>
          </button>
          <button
            className={tab === "roles" ? "active" : ""}
            onClick={() => onTab("roles")}
          >
            <ShieldCheck size={17} /> 角色与权限
            <span>5</span>
          </button>
        </div>

        {tab === "users" && (
          <div className="panel admin-panel">
            <div className="admin-panel-head">
              <label className="search-field">
                <Search size={16} />
                <input placeholder="搜索姓名或账号" />
              </label>
              <button className="secondary-button">
                <Filter size={15} /> 筛选
              </button>
            </div>
            <div className="data-table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>成员</th>
                    <th>所属部门</th>
                    <th>角色</th>
                    <th>状态</th>
                    <th>最近登录</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {teamUsers.map((member, index) => (
                    <tr key={member.account}>
                      <td>
                        <div className="member-cell">
                          <span className="avatar">{member.name.slice(0, 1)}</span>
                          <span>
                            <strong>{member.name}</strong>
                            <small>@{member.account}</small>
                          </span>
                        </div>
                      </td>
                      <td>{member.dept}</td>
                      <td>
                        <span className="role-chip">{member.role}</span>
                      </td>
                      <td>
                        <StatusBadge value={member.status} />
                      </td>
                      <td className="muted-cell">
                        {index < 3 ? "今天 " + (10 - index) + ":24" : "昨天 17:40"}
                      </td>
                      <td>
                        <button className="icon-button">
                          <MoreHorizontal size={17} />
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {tab === "depts" && (
          <div className="department-grid">
            {[
              ["总公司", "根部门", 8, 7],
              ["第一生产车间", "注塑与成型作业区", 2, 2],
              ["第二生产车间", "机加工与装配作业区", 2, 3],
              ["设备维保科", "设备维修与保养归口部门", 3, 2],
            ].map(([name, description, members, assets], index) => (
              <div className="panel dept-card" key={String(name)}>
                <div>
                  <span className="dept-icon">
                    <Building2 size={19} />
                  </span>
                  {index === 0 && <em>根部门</em>}
                  <button className="icon-button">
                    <MoreHorizontal size={17} />
                  </button>
                </div>
                <h3>{name}</h3>
                <p>{description}</p>
                <div>
                  <span>
                    <Users size={15} /> {members} 人
                  </span>
                  <span>
                    <Boxes size={15} /> {assets} 台设备
                  </span>
                </div>
              </div>
            ))}
          </div>
        )}

        {tab === "roles" && (
          <div className="role-layout">
            <aside className="panel role-list">
              <p>内置角色</p>
              {[
                ["普通员工", "EMPLOYEE", 5],
                ["部门主管", "DEPT_MANAGER", 10],
                ["维修工程师", "ENGINEER", 6],
                ["维保主管", "MAINT_MANAGER", 10],
                ["系统管理员", "ADMIN", 7],
              ].map(([name, code, count], index) => (
                <button className={index === 3 ? "active" : ""} key={String(code)}>
                  <span className="role-symbol">
                    <ShieldCheck size={17} />
                  </span>
                  <span>
                    <strong>{name}</strong>
                    <small>{code}</small>
                  </span>
                  <em>{count} 项权限</em>
                </button>
              ))}
            </aside>
            <section className="panel permission-panel">
              <div className="panel-heading">
                <div>
                  <span>MAINT_MANAGER</span>
                  <h2>维保主管权限</h2>
                </div>
                <button className="secondary-button">
                  <Settings size={15} /> 编辑权限
                </button>
              </div>
              <p className="permission-note">
                权限变更将在用户下一次请求时生效。有效权限为该用户全部角色权限的并集。
              </p>
              <div className="permission-groups">
                {[
                  {
                    title: "设备台账",
                    items: ["查看设备", "新增设备", "修改设备", "删除设备"],
                  },
                  {
                    title: "维修工单",
                    items: ["查看工单", "接单", "开始维修", "委外处理", "提交验收", "撤销工单"],
                  },
                  {
                    title: "组织与账户",
                    items: ["查看部门", "管理部门", "查看账户", "管理账户"],
                  },
                ].map((group, groupIndex) => (
                  <div key={group.title}>
                    <div className="permission-group-title">
                      <strong>{group.title}</strong>
                      <span>
                        {groupIndex < 2
                          ? `${group.items.length}/${group.items.length}`
                          : "0/4"}
                      </span>
                    </div>
                    <div className="permission-checks">
                      {group.items.map((item) => (
                        <label key={item}>
                          <input
                            type="checkbox"
                            checked={groupIndex < 2}
                            readOnly
                          />
                          <span>
                            <Check size={13} />
                          </span>
                          {item}
                        </label>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            </section>
          </div>
        )}
      </section>

      <section className="security-principle">
        <ShieldCheck size={20} />
        <div>
          <strong>当前身份：{user.realName}</strong>
          <p>
            你拥有 {user.permissions.length} 个权限码。页面显隐来自
            <code>GET /auth/me</code>，接口仍会独立完成权限和数据范围校验。
          </p>
        </div>
      </section>
    </div>
  );
}

function AuditView() {
  return (
    <div className="view-stack">
      <PageHeading
        eyebrow="TRACEABLE BY DESIGN"
        title="审计与监控"
        description="通过 traceId 将业务操作、应用日志与系统状态串联起来。"
        actions={
          <button className="secondary-button">
            <Download size={16} /> 导出审计记录
          </button>
        }
      />
      <section className="health-strip">
        {[
          ["核心 API", "健康", "23 ms", Activity],
          ["MySQL", "健康", "8 ms", Boxes],
          ["Redis", "规划中", "Day 13", Zap],
          ["RepairMind", "规划中", "Day 31", Bot],
        ].map(([name, state, metric, Icon], index) => {
          const ItemIcon = Icon as LucideIcon;
          return (
            <div className="health-service" key={String(name)}>
              <span className={index < 2 ? "healthy" : "planned"}>
                <ItemIcon size={18} />
              </span>
              <div>
                <strong>{name as string}</strong>
                <small>{state as string}</small>
              </div>
              <em>{metric as string}</em>
            </div>
          );
        })}
      </section>
      <section className="audit-grid">
        <div className="panel audit-chart-panel">
          <div className="panel-heading">
            <div>
              <span>HTTP REQUESTS</span>
              <h2>请求量与错误率</h2>
            </div>
            <span className="select-chip">近 24 小时 <ChevronDown size={14} /></span>
          </div>
          <div className="line-chart-sim">
            <div className="axis-labels">
              <span>1.2k</span>
              <span>800</span>
              <span>400</span>
              <span>0</span>
            </div>
            <div className="chart-lines">
              {[28, 42, 34, 56, 48, 67, 59, 74, 68, 82, 64, 71].map(
                (height, index) => (
                  <span
                    key={index}
                    style={{ height: `${height}%` }}
                    className={index === 8 ? "hot" : ""}
                  />
                ),
              )}
            </div>
          </div>
          <div className="audit-kpis">
            <span>
              <strong>18,429</strong>
              <small>请求总量</small>
            </span>
            <span>
              <strong>0.16%</strong>
              <small>错误率</small>
            </span>
            <span>
              <strong>P95 84ms</strong>
              <small>响应时间</small>
            </span>
          </div>
        </div>
        <div className="panel risk-panel">
          <div className="panel-heading">
            <div>
              <span>SECURITY EVENTS</span>
              <h2>安全事件</h2>
            </div>
            <span className="warning-count">1 待关注</span>
          </div>
          <div className="risk-event">
            <span className="risk-icon">
              <AlertTriangle size={18} />
            </span>
            <div>
              <strong>跨部门附件访问被拒绝</strong>
              <p>用户张三尝试下载第二生产车间设备附件，服务端返回 403。</p>
              <small>09:57 · TR-7H4KQ6</small>
            </div>
          </div>
          <div className="risk-ok">
            <CheckCircle2 size={17} />
            <span>
              <strong>敏感信息检查通过</strong>
              <small>日志中未发现令牌、密码或密钥</small>
            </span>
          </div>
        </div>
      </section>
      <section className="panel table-panel audit-table">
        <div className="panel-heading">
          <div>
            <span>AUDIT TRAIL</span>
            <h2>最近操作</h2>
          </div>
          <label className="search-field compact">
            <Search size={15} />
            <input placeholder="搜索 traceId 或资源" />
          </label>
        </div>
        <div className="data-table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>时间</th>
                <th>操作人</th>
                <th>动作</th>
                <th>目标资源</th>
                <th>结果</th>
                <th>traceId</th>
                <th>来源</th>
              </tr>
            </thead>
            <tbody>
              {auditRows.map((row) => (
                <tr key={row.id}>
                  <td className="muted-cell">{row.time}</td>
                  <td>{row.actor}</td>
                  <td>{row.action}</td>
                  <td className="table-primary">{row.resource}</td>
                  <td>
                    <StatusBadge value={row.result} />
                  </td>
                  <td>
                    <code>{row.id}</code>
                  </td>
                  <td className="muted-cell">{row.ip}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}

function RoadmapView() {
  return (
    <div className="view-stack">
      <PageHeading
        eyebrow="DAY 12 / 51 · P1 IN PROGRESS"
        title="建设进度"
        description="前端已按最终蓝图展开，后端能力按执行手册逐日接入。"
        actions={
          <span className="roadmap-day-chip">
            <CalendarClock size={16} /> 当前 Day 12
          </span>
        }
      />
      <section className="roadmap-hero">
        <div>
          <span>总体完成度</span>
          <strong>23.5%</strong>
          <p>
            地基阶段已验收，正在完善设备附件安全。下一站：Redis
            缓存与一致性。
          </p>
        </div>
        <div className="roadmap-ring">
          <div>
            <strong>12</strong>
            <span>/ 51 DAYS</span>
          </div>
        </div>
        <div className="today-checklist">
          <span>今日验收线</span>
          {[
            ["安全上传设备附件", true],
            ["校验文件类型与大小", true],
            ["跨部门下载返回 403", true],
            ["文件服务集成测试", false],
          ].map(([task, done]) => (
            <div key={String(task)}>
              <span className={done ? "done" : ""}>
                {done ? <Check size={13} /> : <CircleDot size={12} />}
              </span>
              <p>{task as string}</p>
            </div>
          ))}
        </div>
      </section>

      <section className="phase-list">
        {phases.map((phase, index) => (
          <article
            className={`phase-card ${phase.status === "进行中" ? "current" : ""}`}
            key={phase.code}
          >
            <div className="phase-index">
              <span>{phase.code}</span>
              {index < phases.length - 1 && <i />}
            </div>
            <div className="phase-content">
              <div>
                <span>{phase.range}</span>
                <StatusBadge value={phase.status} />
              </div>
              <h2>{phase.title}</h2>
              <p>{phase.detail}</p>
              <div className="phase-progress">
                <span>
                  <i style={{ width: `${phase.progress}%` }} />
                </span>
                <em>{phase.progress}%</em>
              </div>
            </div>
            {phase.status === "进行中" && (
              <div className="phase-current-mark">
                <Radio size={14} /> NOW
              </div>
            )}
          </article>
        ))}
      </section>

      <section className="roadmap-bottom-grid">
        <div className="panel next-days">
          <div className="panel-heading">
            <div>
              <span>NEXT UP</span>
              <h2>接下来 5 天</h2>
            </div>
          </div>
          {[
            ["Day 13", "Redis 缓存与一致性", "明天"],
            ["Day 14", "缓存击穿实验 + 开始投递", "7/28"],
            ["Day 15", "工单并发与 Redisson", "7/29"],
            ["Day 16", "Security 7 与工单状态机", "7/30"],
            ["Day 17", "校验、幂等与审计日志", "7/31"],
          ].map(([day, task, date]) => (
            <div className="next-day-row" key={day}>
              <span>{day}</span>
              <strong>{task}</strong>
              <small>{date}</small>
            </div>
          ))}
        </div>
        <div className="panel integration-map">
          <div className="panel-heading">
            <div>
              <span>FRONTEND READINESS</span>
              <h2>模块接通状态</h2>
            </div>
          </div>
          {[
            ["认证与当前用户", "已接通", 100],
            ["设备台账 CRUD", "已接通", 100],
            ["设备附件", "等待 Controller", 75],
            ["维修工单", "Day 15–17", 20],
            ["RepairMind AI", "Day 31 起", 8],
          ].map(([label, status, progress]) => (
            <div className="integration-row" key={String(label)}>
              <div>
                <strong>{label}</strong>
                <small>{status}</small>
              </div>
              <span>
                <i style={{ width: `${progress}%` }} />
              </span>
              <em>{progress}%</em>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}

function StatusBadge({ value }: { value: string }) {
  const statusMap: Record<string, string> = {
    正常: "success",
    启用: "success",
    成功: "success",
    健康: "success",
    已完成: "success",
    已接通: "success",
    维修中: "warning",
    委外中: "warning",
    待验收: "purple",
    已接单: "blue",
    待受理: "neutral",
    停用: "neutral",
    已关闭: "neutral",
    报废: "danger",
    拒绝: "danger",
    进行中: "blue",
    未开始: "neutral",
  };
  return (
    <span className={`status-badge ${statusMap[value] || "neutral"}`}>
      <i />
      {value}
    </span>
  );
}

function DeviceDrawer({
  device,
  canEdit,
  onClose,
  onReport,
  onNotify,
}: {
  device: Device;
  canEdit: boolean;
  onClose: () => void;
  onReport: () => void;
  onNotify: (message: string) => void;
}) {
  const [tab, setTab] = useState<"overview" | "files" | "history">("overview");
  return (
    <Drawer onClose={onClose} width="wide">
      <div className="drawer-heading">
        <div className="asset-large-icon">
          <Boxes size={23} />
        </div>
        <div>
          <span>{device.code}</span>
          <h2>{device.name}</h2>
          <p>{device.model}</p>
        </div>
        <StatusBadge value={device.status} />
        <button className="icon-button" onClick={onClose} aria-label="关闭">
          <X size={19} />
        </button>
      </div>
      <div className="drawer-tabs">
        {[
          ["overview", "设备概览"],
          ["files", "安全附件"],
          ["history", "维修记录"],
        ].map(([id, label]) => (
          <button
            key={id}
            className={tab === id ? "active" : ""}
            onClick={() => setTab(id as typeof tab)}
          >
            {label}
          </button>
        ))}
      </div>
      <div className="drawer-body">
        {tab === "overview" && (
          <>
            <div className="detail-health-card">
              <div>
                <span>设备健康度</span>
                <strong>{device.health ?? 92}</strong>
                <em>/ 100</em>
              </div>
              <div className="health-meter large">
                <span>
                  <i style={{ width: `${device.health ?? 92}%` }} />
                </span>
              </div>
              <small>综合运行状态、工单与点检记录评估</small>
            </div>
            <div className="detail-grid">
              {[
                ["安装位置", device.location],
                ["归属部门", device.deptName],
                ["设备责任人", device.ownerName],
                ["累计运行", `${device.runningHours ?? "—"} h`],
                ["最近点检", device.lastInspection || "—"],
                ["最后更新", device.updateTime],
              ].map(([label, value]) => (
                <div key={label}>
                  <span>{label}</span>
                  <strong>{value}</strong>
                </div>
              ))}
            </div>
            <div className="detail-section">
              <span>设备说明</span>
              <p>{device.description || "暂无设备说明。"}</p>
            </div>
            <div className="detail-section">
              <div className="detail-section-title">
                <span>实时信号</span>
                <em>
                  <Radio size={12} /> 预览数据
                </em>
              </div>
              <div className="signal-grid">
                <div>
                  <Activity size={17} />
                  <span>振动</span>
                  <strong>2.8 mm/s</strong>
                  <small>注意</small>
                </div>
                <div>
                  <Gauge size={17} />
                  <span>主轴转速</span>
                  <strong>7,860 rpm</strong>
                  <small>运行中</small>
                </div>
                <div>
                  <Zap size={17} />
                  <span>负载</span>
                  <strong>68%</strong>
                  <small>正常</small>
                </div>
              </div>
            </div>
          </>
        )}
        {tab === "files" && (
          <div className="files-section">
            <div className="file-safety-note">
              <ShieldCheck size={18} />
              <div>
                <strong>安全附件</strong>
                <p>上传会校验真实文件类型、大小与访问范围，存储名不使用原始文件名。</p>
              </div>
            </div>
            {[
              ["DMU50_维护手册.pdf", "PDF · 8.4 MB", "2026-04-18"],
              ["主轴结构示意图.png", "PNG · 2.1 MB", "2026-06-03"],
              ["验收记录.pdf", "PDF · 640 KB", "2026-04-22"],
            ].map(([name, meta, date]) => (
              <div className="file-row" key={name}>
                <span className="file-icon">
                  <FileText size={17} />
                </span>
                <span>
                  <strong>{name}</strong>
                  <small>
                    {meta} · {date}
                  </small>
                </span>
                <button
                  className="icon-button"
                  onClick={() => onNotify("预览模式：下载前会由后端校验设备访问范围")}
                >
                  <Download size={16} />
                </button>
              </div>
            ))}
            {canEdit && (
              <button
                className="upload-zone"
                onClick={() => onNotify("文件接口 Controller 完成后将在这里接通上传")}
              >
                <Upload size={21} />
                <strong>上传设备附件</strong>
                <span>支持 PDF、PNG、JPG，单个文件不超过 20 MB</span>
              </button>
            )}
          </div>
        )}
        {tab === "history" && (
          <div className="timeline">
            {[
              ["今天 10:42", "开始维修", "孙七", "正在定位主轴高速段振动源"],
              ["今天 09:02", "接单", "孙七", "已接单，设备保持停机"],
              ["今天 08:34", "创建报修", "李四", "主轴高速段出现周期性异响"],
              ["05月12日", "维修完成", "周八", "校准刀库换刀位置，试运行通过"],
            ].map(([time, action, actor, note], index) => (
              <div key={time + action}>
                <span className={index === 0 ? "active" : ""}>
                  {index === 0 ? <Wrench size={14} /> : <Check size={13} />}
                </span>
                <div>
                  <small>{time}</small>
                  <strong>{action}</strong>
                  <p>{note}</p>
                  <em>操作人 · {actor}</em>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
      <div className="drawer-footer">
        {canEdit && <button className="secondary-button">编辑设备</button>}
        <button className="primary-button" onClick={onReport}>
          <Wrench size={16} /> 发起报修
        </button>
      </div>
    </Drawer>
  );
}

function OrderDrawer({
  order,
  canAdvance,
  onAdvance,
  onClose,
}: {
  order: WorkOrder;
  canAdvance: boolean;
  onAdvance: () => void;
  onClose: () => void;
}) {
  const actionLabel: Partial<Record<OrderStatus, string>> = {
    待受理: "接下工单",
    已接单: "开始维修",
    维修中: "提交验收",
    委外中: "提交验收",
    待验收: "验收通过",
  };
  return (
    <Drawer onClose={onClose} width="wide">
      <div className="drawer-heading order-drawer-heading">
        <div>
          <span>{order.code}</span>
          <h2>{order.title}</h2>
          <p>
            {order.deviceName} · {order.deviceCode}
          </p>
        </div>
        <StatusBadge value={order.status} />
        <button className="icon-button" onClick={onClose} aria-label="关闭">
          <X size={19} />
        </button>
      </div>
      <div className="order-progress-hero">
        <div>
          <span>当前进度</span>
          <strong>{order.progress}%</strong>
        </div>
        <div className="progress-track">
          <span style={{ width: `${order.progress}%` }} />
        </div>
        <small>
          已用时 {order.elapsed}
          {order.slaRisk && <em> · 存在 SLA 超时风险</em>}
        </small>
      </div>
      <div className="drawer-body">
        <div className="detail-grid">
          {[
            ["紧急程度", order.priority],
            ["报修人", order.reporter],
            ["当前处理人", order.assignee || "待指派"],
            ["发生位置", order.location],
            ["创建时间", order.createdAt],
            ["当前状态", order.status],
          ].map(([label, value]) => (
            <div key={label}>
              <span>{label}</span>
              <strong>{value}</strong>
            </div>
          ))}
        </div>
        <div className="detail-section">
          <span>故障描述</span>
          <p>{order.description}</p>
        </div>
        <div className="detail-section">
          <span>流转记录</span>
          <div className="timeline compact">
            <div>
              <span className="active">
                <Wrench size={14} />
              </span>
              <div>
                <small>今天 10:42</small>
                <strong>{order.status}</strong>
                <p>当前工单已进入「{order.status}」阶段。</p>
              </div>
            </div>
            <div>
              <span>
                <Check size={13} />
              </span>
              <div>
                <small>{order.createdAt}</small>
                <strong>创建报修</strong>
                <p>{order.reporter} 提交了故障描述与紧急程度。</p>
              </div>
            </div>
          </div>
        </div>
        <div className="concurrency-note">
          <Network size={18} />
          <div>
            <strong>并发安全</strong>
            <p>多人同时接单时，仅一个请求可以成功，其余请求会收到明确冲突提示。</p>
          </div>
        </div>
      </div>
      <div className="drawer-footer">
        <button className="secondary-button">添加维修记录</button>
        {canAdvance && actionLabel[order.status] && (
          <button className="primary-button" onClick={onAdvance}>
            <Play size={16} /> {actionLabel[order.status]}
          </button>
        )}
      </div>
    </Drawer>
  );
}

function Drawer({
  children,
  onClose,
  width,
}: {
  children: ReactNode;
  onClose: () => void;
  width?: "wide";
}) {
  return (
    <div className="overlay-layer">
      <button className="overlay-backdrop" onClick={onClose} aria-label="关闭" />
      <aside className={`drawer ${width || ""}`}>{children}</aside>
    </div>
  );
}

function Modal({
  title,
  description,
  children,
  onClose,
}: {
  title: string;
  description: string;
  children: ReactNode;
  onClose: () => void;
}) {
  return (
    <div className="overlay-layer modal-layer">
      <button className="overlay-backdrop" onClick={onClose} aria-label="关闭" />
      <section className="modal-card" role="dialog" aria-modal="true">
        <header>
          <div>
            <span>CREATE RECORD</span>
            <h2>{title}</h2>
            <p>{description}</p>
          </div>
          <button className="icon-button" onClick={onClose} aria-label="关闭">
            <X size={19} />
          </button>
        </header>
        {children}
      </section>
    </div>
  );
}

function DeviceModal({
  onClose,
  onSubmit,
}: {
  onClose: () => void;
  onSubmit: (input: DeviceInput) => Promise<boolean>;
}) {
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState<DeviceInput>({
    code: "",
    name: "",
    model: "",
    location: "",
    ownerId: 1,
    description: "",
  });
  async function submit(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    await onSubmit(form);
    setSaving(false);
  }
  return (
    <Modal
      title="新建设备"
      description="设备编号创建后不可修改，请核对后提交。"
      onClose={onClose}
    >
      <form className="modal-form" onSubmit={submit}>
        <div className="form-grid">
          <label className="form-field">
            <span>设备编号 *</span>
            <input
              required
              maxLength={50}
              placeholder="例如 EQ-CNC-0018"
              value={form.code}
              onChange={(event) => setForm({ ...form, code: event.target.value })}
            />
          </label>
          <label className="form-field">
            <span>设备名称 *</span>
            <input
              required
              maxLength={50}
              placeholder="请输入设备名称"
              value={form.name}
              onChange={(event) => setForm({ ...form, name: event.target.value })}
            />
          </label>
          <label className="form-field">
            <span>型号 *</span>
            <input
              required
              maxLength={50}
              placeholder="请输入设备型号"
              value={form.model}
              onChange={(event) => setForm({ ...form, model: event.target.value })}
            />
          </label>
          <label className="form-field">
            <span>安装位置 *</span>
            <input
              required
              maxLength={50}
              placeholder="例如 第二生产车间 · A04"
              value={form.location}
              onChange={(event) =>
                setForm({ ...form, location: event.target.value })
              }
            />
          </label>
          <label className="form-field">
            <span>责任人 ID *</span>
            <input
              required
              min={1}
              type="number"
              value={form.ownerId}
              onChange={(event) =>
                setForm({ ...form, ownerId: Number(event.target.value) })
              }
            />
          </label>
          <label className="form-field">
            <span>初始状态</span>
            <input value="正常（由后端流程维护）" disabled />
          </label>
        </div>
        <label className="form-field">
          <span>设备说明</span>
          <textarea
            rows={3}
            maxLength={255}
            placeholder="用途、关键参数或注意事项"
            value={form.description}
            onChange={(event) =>
              setForm({ ...form, description: event.target.value })
            }
          />
        </label>
        <footer className="modal-actions">
          <button type="button" className="secondary-button" onClick={onClose}>
            取消
          </button>
          <button className="primary-button" disabled={saving}>
            {saving ? "正在保存..." : "创建设备"}
          </button>
        </footer>
      </form>
    </Modal>
  );
}

function OrderModal({
  devices,
  onClose,
  onSubmit,
}: {
  devices: Device[];
  onClose: () => void;
  onSubmit: (input: {
    deviceCode: string;
    title: string;
    priority: Priority;
    description: string;
  }) => void;
}) {
  const [form, setForm] = useState<{
    deviceCode: string;
    title: string;
    priority: Priority;
    description: string;
  }>({
    deviceCode: devices[0]?.code || "",
    title: "",
    priority: "中",
    description: "",
  });
  return (
    <Modal
      title="发起报修"
      description="同一次提交会携带幂等键，重复点击不会创建多张工单。"
      onClose={onClose}
    >
      <form
        className="modal-form"
        onSubmit={(event) => {
          event.preventDefault();
          onSubmit(form);
        }}
      >
        <label className="form-field">
          <span>故障设备 *</span>
          <select
            required
            value={form.deviceCode}
            onChange={(event) =>
              setForm({ ...form, deviceCode: event.target.value })
            }
          >
            {devices.map((device) => (
              <option value={device.code} key={device.id}>
                {device.code} · {device.name}
              </option>
            ))}
          </select>
        </label>
        <label className="form-field">
          <span>故障标题 *</span>
          <input
            required
            placeholder="用一句话描述主要现象"
            value={form.title}
            onChange={(event) => setForm({ ...form, title: event.target.value })}
          />
        </label>
        <fieldset className="priority-picker">
          <legend>紧急程度 *</legend>
          {(["低", "中", "高", "紧急"] as Priority[]).map((priority) => (
            <label key={priority} className={form.priority === priority ? "active" : ""}>
              <input
                type="radio"
                name="priority"
                value={priority}
                checked={form.priority === priority}
                onChange={() => setForm({ ...form, priority })}
              />
              <span className={priorityClass[priority]} />
              {priority}
            </label>
          ))}
        </fieldset>
        <label className="form-field">
          <span>故障现象 *</span>
          <textarea
            required
            rows={5}
            placeholder="请描述出现时间、现象、频率和已采取的措施"
            value={form.description}
            onChange={(event) =>
              setForm({ ...form, description: event.target.value })
            }
          />
        </label>
        <div className="idempotency-tip">
          <ShieldCheck size={16} />
          创建请求将使用数据库唯一约束兜底幂等，即使 Redis 不可用也不会重复建单。
        </div>
        <footer className="modal-actions">
          <button type="button" className="secondary-button" onClick={onClose}>
            取消
          </button>
          <button className="primary-button">确认报修</button>
        </footer>
      </form>
    </Modal>
  );
}

function NotificationPanel({ onClose }: { onClose: () => void }) {
  return (
    <div className="notification-panel">
      <header>
        <div>
          <span>NOTIFICATIONS</span>
          <strong>通知中心</strong>
        </div>
        <button className="icon-button" onClick={onClose}>
          <X size={16} />
        </button>
      </header>
      <button className="notification-row unread">
        <span className="notification-icon warning">
          <AlertTriangle size={17} />
        </span>
        <span>
          <strong>工单即将触发 SLA 超时</strong>
          <p>WO-20260726-050 已等待受理 1 小时。</p>
          <small>6 分钟前</small>
        </span>
      </button>
      <button className="notification-row unread">
        <span className="notification-icon blue">
          <Wrench size={17} />
        </span>
        <span>
          <strong>孙七开始处理你的工单</strong>
          <p>五轴加工中心异响排查已进入维修中。</p>
          <small>18 分钟前</small>
        </span>
      </button>
      <button className="notification-row">
        <span className="notification-icon success">
          <CheckCircle2 size={17} />
        </span>
        <span>
          <strong>设备附件安全检查通过</strong>
          <p>3 份新附件已完成类型与大小校验。</p>
          <small>1 小时前</small>
        </span>
      </button>
      <footer>
        <button>全部标记为已读</button>
      </footer>
    </div>
  );
}

function CommandPalette({
  navGroups,
  devices,
  onNavigate,
  onDevice,
  onClose,
}: {
  navGroups: { label: string; items: NavItem[] }[];
  devices: Device[];
  onNavigate: (view: WorkspaceView) => void;
  onDevice: (device: Device) => void;
  onClose: () => void;
}) {
  const [query, setQuery] = useState("");
  const normalized = query.toLowerCase();
  const matchingNav = navGroups
    .flatMap((group) => group.items)
    .filter((item) => item.label.toLowerCase().includes(normalized));
  const matchingDevices = devices
    .filter(
      (device) =>
        device.name.toLowerCase().includes(normalized) ||
        device.code.toLowerCase().includes(normalized),
    )
    .slice(0, 4);
  return (
    <div className="overlay-layer command-layer">
      <button className="overlay-backdrop" onClick={onClose} aria-label="关闭" />
      <section className="command-palette">
        <label>
          <Search size={19} />
          <input
            autoFocus
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="搜索页面、设备编号或名称..."
          />
          <kbd>ESC</kbd>
        </label>
        <div className="command-results">
          <p>快速前往</p>
          {matchingNav.map((item) => {
            const Icon = item.icon;
            return (
              <button key={item.id} onClick={() => onNavigate(item.id)}>
                <Icon size={17} />
                <span>{item.label}</span>
                <ArrowRight size={14} />
              </button>
            );
          })}
          {matchingDevices.length > 0 && <p>设备</p>}
          {matchingDevices.map((device) => (
            <button key={device.id} onClick={() => onDevice(device)}>
              <Boxes size={17} />
              <span>
                {device.name} <small>{device.code}</small>
              </span>
              <StatusBadge value={device.status} />
            </button>
          ))}
        </div>
      </section>
    </div>
  );
}
