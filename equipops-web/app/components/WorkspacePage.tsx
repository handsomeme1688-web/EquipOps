"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { getCurrentUser, hasStoredSession } from "../lib/api";
import { previewUser } from "../lib/preview-user";
import {
  workspacePath,
  type AdminTab,
  type WorkspaceView,
} from "../lib/routes";
import type { ConnectionMode, CurrentUser } from "../lib/types";
import { EquipOpsApp } from "./EquipOpsApp";

interface WorkspacePageProps {
  view: WorkspaceView;
  adminTab?: AdminTab;
}

const viewPermission: Partial<Record<WorkspaceView, string>> = {
  devices: "device:view",
  orders: "order:view",
  admin: "system:user:view",
};

export function WorkspacePage({
  view,
  adminTab = "users",
}: WorkspacePageProps) {
  const router = useRouter();
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [connectionMode, setConnectionMode] =
    useState<ConnectionMode>("preview");
  const [checking, setChecking] = useState(true);

  useEffect(() => {
    let active = true;

    async function restore() {
      if (sessionStorage.getItem("equipops_preview_mode") === "true") {
        if (active) {
          setUser(previewUser);
          setConnectionMode("preview");
          setChecking(false);
        }
        return;
      }

      if (!hasStoredSession()) {
        router.replace("/login");
        return;
      }

      try {
        const currentUser = await getCurrentUser();
        const requiredPermission = viewPermission[view];
        if (
          requiredPermission &&
          !currentUser.permissions.includes(requiredPermission)
        ) {
          router.replace("/overview");
          return;
        }
        if (active) {
          setUser(currentUser);
          setConnectionMode("online");
        }
      } catch {
        localStorage.removeItem("equipops_access_token");
        router.replace("/login");
      } finally {
        if (active) setChecking(false);
      }
    }

    restore();
    return () => {
      active = false;
    };
  }, [router, view]);

  if (checking || !user) {
    return (
      <main className="boot-screen" aria-live="polite">
        <div className="boot-mark">
          <span />
          <span />
          <span />
        </div>
        <p>正在校验工作台会话</p>
      </main>
    );
  }

  return (
    <EquipOpsApp
      user={user}
      connectionMode={connectionMode}
      view={view}
      adminTab={adminTab}
      onNavigate={(nextView, nextAdminTab) =>
        router.push(workspacePath(nextView, nextAdminTab))
      }
      onSignOut={() => {
        localStorage.removeItem("equipops_access_token");
        sessionStorage.removeItem("equipops_preview_mode");
        router.replace("/login");
      }}
    />
  );
}
