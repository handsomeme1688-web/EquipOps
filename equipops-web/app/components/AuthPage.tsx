"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { getCurrentUser, hasStoredSession } from "../lib/api";
import type { AuthMode } from "../lib/routes";
import { LoginScreen } from "./LoginScreen";

interface AuthPageProps {
  mode: AuthMode;
  initialUsername?: string;
  registrationSuccess?: boolean;
}

export function AuthPage({
  mode,
  initialUsername = "",
  registrationSuccess = false,
}: AuthPageProps) {
  const router = useRouter();

  useEffect(() => {
    let active = true;
    if (!hasStoredSession()) return;

    getCurrentUser()
      .then(() => {
        if (active) router.replace("/overview");
      })
      .catch(() => {
        localStorage.removeItem("equipops_access_token");
      });

    return () => {
      active = false;
    };
  }, [router]);

  return (
    <LoginScreen
      initialMode={mode}
      initialUsername={initialUsername}
      registrationSuccess={registrationSuccess}
      onModeChange={(nextMode) => router.push(`/${nextMode}`)}
      onRegistered={(username) => {
        router.push(
          `/login?registered=1&username=${encodeURIComponent(username)}`,
        );
      }}
      onAuthenticated={() => {
        sessionStorage.removeItem("equipops_preview_mode");
        router.replace("/overview");
      }}
      onPreview={() => {
        sessionStorage.setItem("equipops_preview_mode", "true");
        router.push("/overview");
      }}
    />
  );
}
