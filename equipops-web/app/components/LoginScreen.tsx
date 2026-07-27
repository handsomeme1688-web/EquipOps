"use client";

import {
  ArrowRight,
  Bot,
  Boxes,
  CheckCircle2,
  Eye,
  EyeOff,
  Gauge,
  LockKeyhole,
  Mail,
  Phone,
  ShieldCheck,
  Sparkles,
  UserRound,
  Wrench,
} from "lucide-react";
import { FormEvent, useEffect, useState } from "react";
import { getCurrentUser, login, register } from "../lib/api";
import type { AuthMode } from "../lib/routes";
import type { ConnectionMode, CurrentUser } from "../lib/types";

interface LoginScreenProps {
  initialMode: AuthMode;
  initialUsername?: string;
  registrationSuccess?: boolean;
  onModeChange: (mode: AuthMode) => void;
  onRegistered: (username: string) => void;
  onAuthenticated: (user: CurrentUser, mode: ConnectionMode) => void;
  onPreview: () => void;
}

export function LoginScreen({
  initialMode,
  initialUsername = "",
  registrationSuccess = false,
  onModeChange,
  onRegistered,
  onAuthenticated,
  onPreview,
}: LoginScreenProps) {
  const mode = initialMode;
  const [username, setUsername] = useState(initialUsername);
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [realName, setRealName] = useState("");
  const [deptId, setDeptId] = useState(2);
  const [phone, setPhone] = useState("");
  const [email, setEmail] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [verificationCode, setVerificationCode] = useState("");
  const [issuedCode, setIssuedCode] = useState("");
  const [issuedPhone, setIssuedPhone] = useState("");
  const [verifiedPhone, setVerifiedPhone] = useState("");
  const [verificationNotice, setVerificationNotice] = useState("");
  const [cooldown, setCooldown] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState(
    registrationSuccess ? "注册成功，请使用用户名和密码重新登录。" : "",
  );

  const normalizedPhone = phone.trim();
  const phonePattern = /^1[3-9]\d{9}$/;
  const isPhoneVerified =
    normalizedPhone.length > 0 && verifiedPhone === normalizedPhone;

  useEffect(() => {
    if (cooldown <= 0) return;

    const timer = window.setInterval(() => {
      setCooldown((value) => Math.max(0, value - 1));
    }, 1000);

    return () => window.clearInterval(timer);
  }, [cooldown]);

  function handlePhoneChange(value: string) {
    const nextPhone = value.replace(/\D/g, "").slice(0, 11);
    setPhone(nextPhone);
    if (nextPhone !== issuedPhone) {
      setIssuedCode("");
      setIssuedPhone("");
      setVerificationCode("");
      setVerifiedPhone("");
      setVerificationNotice("");
      setCooldown(0);
    }
  }

  function sendVerificationCode() {
    setError("");
    setSuccess("");
    if (!phonePattern.test(normalizedPhone)) {
      setError("请输入有效的 11 位手机号");
      return;
    }

    const nextCode = String(Math.floor(100000 + Math.random() * 900000));
    setIssuedCode(nextCode);
    setIssuedPhone(normalizedPhone);
    setVerificationCode("");
    setVerifiedPhone("");
    setCooldown(60);
    setVerificationNotice(
      `开发验证码：${nextCode}（短信服务接入前，仅用于本地验证）`,
    );
  }

  function verifyPhoneCode() {
    setError("");
    setSuccess("");
    if (!issuedCode || issuedPhone !== normalizedPhone) {
      setError("请先向当前手机号发送验证码");
      return;
    }
    if (verificationCode !== issuedCode) {
      setVerifiedPhone("");
      setError("验证码不正确，请重新输入");
      return;
    }

    setVerifiedPhone(normalizedPhone);
    setVerificationNotice("手机号验证通过，可以提交注册。");
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setSuccess("");
    if (!username.trim() || !password) {
      setError("请输入用户名和密码");
      return;
    }
    if (mode === "register" && !realName.trim()) {
      setError("请输入真实姓名");
      return;
    }
    if (mode === "register" && password.length < 6) {
      setError("密码至少需要 6 位");
      return;
    }
    if (mode === "register" && password !== confirmPassword) {
      setError("两次输入的密码不一致");
      return;
    }
    if (mode === "register" && !phonePattern.test(normalizedPhone)) {
      setError("请输入有效的 11 位手机号");
      return;
    }
    if (mode === "register" && !isPhoneVerified) {
      setError("请先完成手机号验证码验证");
      return;
    }
    setLoading(true);
    setError("");
    try {
      if (mode === "login") {
        await login(username.trim(), password);
        const user = await getCurrentUser();
        onAuthenticated(user, "online");
      } else {
        const registeredUsername = username.trim();
        await register({
          username: registeredUsername,
          password,
          realName: realName.trim(),
          deptId,
          phone: normalizedPhone,
          email: email.trim() || undefined,
        });
        onRegistered(registeredUsername);
      }
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : "连接后端失败，请检查服务是否启动",
      );
    } finally {
      setLoading(false);
    }
  }

  function switchMode(nextMode: AuthMode) {
    setError("");
    setSuccess("");
    setPassword("");
    setConfirmPassword("");
    setShowPassword(false);
    setShowConfirmPassword(false);
    onModeChange(nextMode);
  }

  return (
    <main className="login-shell">
      <section className="login-story" aria-label="产品介绍">
        <div className="login-grid" />
        <div className="login-brand">
          <span className="brand-glyph">
            <Gauge size={23} strokeWidth={2.2} />
          </span>
          <div>
            <strong>EquipOps</strong>
            <small>智能设备运维平台</small>
          </div>
        </div>

        <div className="story-copy">
          <div className="eyebrow">
            <span className="pulse-dot" />
            设备 · 工单 · 知识，一处掌握
          </div>
          <h1>
            让每一次故障，
            <br />
            都成为下一次的答案。
          </h1>
          <p>
            从设备台账到维修闭环，再到可追溯的 AI
            辅助诊断。让现场有据可查，让经验持续沉淀。
          </p>
          <div className="story-capabilities">
            <div>
              <Boxes size={20} />
              <span>设备全景台账</span>
            </div>
            <div>
              <Wrench size={20} />
              <span>工单状态闭环</span>
            </div>
            <div>
              <Bot size={20} />
              <span>RepairMind 助手</span>
            </div>
          </div>
        </div>

        <div className="system-proof">
          <div className="proof-item">
            <ShieldCheck size={18} />
            <span>
              <strong>RBAC + 数据隔离</strong>
              <small>权限与部门范围双重校验</small>
            </span>
          </div>
          <div className="proof-divider" />
          <div className="proof-item">
            <Sparkles size={18} />
            <span>
              <strong>AI 引用可追溯</strong>
              <small>安全拒答与受控工具调用</small>
            </span>
          </div>
        </div>
      </section>

      <section className="login-panel">
        <div className="login-form-wrap">
          <div className="mobile-brand">
            <span className="brand-glyph">
              <Gauge size={21} />
            </span>
            <strong>EquipOps</strong>
          </div>
          <div className="login-heading">
            <span>OPERATIONS CONSOLE</span>
            <h2>{mode === "login" ? "欢迎回来" : "创建账户"}</h2>
            <p>
              {mode === "login"
                ? "登录你的运维工作台，继续处理现场任务。"
                : "验证手机号并创建 EquipOps 账户，注册成功后请重新登录。"}
            </p>
          </div>

          <div className="auth-tabs" aria-label="登录或注册">
            <button
              type="button"
              className={mode === "login" ? "active" : ""}
              onClick={() => switchMode("login")}
            >
              登录
            </button>
            <button
              type="button"
              className={mode === "register" ? "active" : ""}
              onClick={() => switchMode("register")}
            >
              注册
            </button>
          </div>

          <form onSubmit={handleSubmit}>
            {mode === "register" && (
              <div className="register-grid">
                <label className="form-field">
                  <span>真实姓名</span>
                  <div className="field-control">
                    <UserRound size={18} />
                    <input
                      autoComplete="name"
                      placeholder="请输入真实姓名"
                      value={realName}
                      onChange={(event) => setRealName(event.target.value)}
                    />
                  </div>
                </label>
                <label className="form-field">
                  <span>所属部门</span>
                  <select
                    value={deptId}
                    onChange={(event) => setDeptId(Number(event.target.value))}
                  >
                    <option value={2}>第一生产车间</option>
                    <option value={3}>第二生产车间</option>
                    <option value={4}>设备维保科</option>
                  </select>
                </label>
              </div>
            )}
            <label className="form-field">
              <span>用户名</span>
              <div className="field-control">
                <input
                  autoComplete="username"
                  placeholder={
                    mode === "login" ? "请输入用户名" : "设置一个用户名"
                  }
                  value={username}
                  onChange={(event) => setUsername(event.target.value)}
                />
              </div>
            </label>
            {mode === "register" && (
              <div className="register-grid">
                <label className="form-field">
                  <span>手机号</span>
                  <div className="field-control">
                    <Phone size={18} />
                    <input
                      required
                      inputMode="numeric"
                      maxLength={11}
                      autoComplete="tel"
                      placeholder="请输入 11 位手机号"
                      value={phone}
                      onChange={(event) =>
                        handlePhoneChange(event.target.value)
                      }
                    />
                  </div>
                </label>
                <label className="form-field">
                  <span>邮箱（选填）</span>
                  <div className="field-control">
                    <Mail size={18} />
                    <input
                      type="email"
                      autoComplete="email"
                      placeholder="name@example.com"
                      value={email}
                      onChange={(event) => setEmail(event.target.value)}
                    />
                  </div>
                </label>
              </div>
            )}
            {mode === "register" && (
              <div className="verification-section">
                <div className="verification-heading">
                  <div>
                    <strong>短信验证码</strong>
                    <small>验证码通过后才能注册</small>
                  </div>
                  <button
                    type="button"
                    className="secondary-button verification-send-button"
                    onClick={sendVerificationCode}
                    disabled={cooldown > 0}
                  >
                    {cooldown > 0 ? `${cooldown} 秒后重发` : "发送验证码"}
                  </button>
                </div>
                <div className="verification-code-row">
                  <div
                    className={`field-control ${
                      isPhoneVerified ? "verified-control" : ""
                    }`}
                  >
                    <ShieldCheck size={18} />
                    <input
                      inputMode="numeric"
                      maxLength={6}
                      autoComplete="one-time-code"
                      placeholder="请输入 6 位验证码"
                      value={verificationCode}
                      onChange={(event) => {
                        setVerificationCode(
                          event.target.value.replace(/\D/g, "").slice(0, 6),
                        );
                        setVerifiedPhone("");
                      }}
                    />
                  </div>
                  <button
                    type="button"
                    className="secondary-button verification-check-button"
                    onClick={verifyPhoneCode}
                    disabled={verificationCode.length !== 6}
                  >
                    验证验证码
                  </button>
                </div>
                {verificationNotice && (
                  <p
                    className={`verification-notice ${
                      isPhoneVerified ? "verified" : ""
                    }`}
                    aria-live="polite"
                  >
                    {isPhoneVerified && <CheckCircle2 size={16} />}
                    <span>{verificationNotice}</span>
                  </p>
                )}
              </div>
            )}
            <label className="form-field">
              <span>密码</span>
              <div className="field-control">
                <LockKeyhole size={17} />
                <input
                  type={showPassword ? "text" : "password"}
                  autoComplete={
                    mode === "login" ? "current-password" : "new-password"
                  }
                  placeholder="请输入密码"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                />
                <button
                  type="button"
                  className="icon-button subtle"
                  onClick={() => setShowPassword((value) => !value)}
                  aria-label={showPassword ? "隐藏密码" : "显示密码"}
                >
                  {showPassword ? <EyeOff size={17} /> : <Eye size={17} />}
                </button>
              </div>
            </label>
            {mode === "register" && (
              <label className="form-field">
                <span>确认密码</span>
                <div className="field-control">
                  <LockKeyhole size={18} />
                  <input
                    type={showConfirmPassword ? "text" : "password"}
                    autoComplete="new-password"
                    placeholder="请再次输入密码"
                    value={confirmPassword}
                    onChange={(event) => setConfirmPassword(event.target.value)}
                  />
                  <button
                    type="button"
                    className="icon-button subtle"
                    onClick={() =>
                      setShowConfirmPassword((value) => !value)
                    }
                    aria-label={
                      showConfirmPassword ? "隐藏确认密码" : "显示确认密码"
                    }
                  >
                    {showConfirmPassword ? (
                      <EyeOff size={17} />
                    ) : (
                      <Eye size={17} />
                    )}
                  </button>
                </div>
              </label>
            )}

            {success && (
              <p className="form-success" aria-live="polite">
                <CheckCircle2 size={17} />
                <span>{success}</span>
              </p>
            )}
            {error && <p className="form-error">{error}</p>}

            <button className="primary-button login-button" disabled={loading}>
              <span>
                {loading
                  ? mode === "login"
                    ? "正在登录..."
                    : "正在创建账户..."
                  : mode === "login"
                    ? "登录工作台"
                    : "验证并注册账户"}
              </span>
              <ArrowRight size={18} />
            </button>
          </form>

          <div className="preview-divider">
            <span>后端尚未启动？</span>
          </div>
          <button className="preview-button" type="button" onClick={onPreview}>
            <Sparkles size={17} />
            进入完整预览模式
          </button>

          <div className="login-note">
            <CheckCircle2 size={15} />
            <span>预览模式不会写入后端数据，可放心体验全部模块。</span>
          </div>
        </div>

        <footer className="login-footer">
          <span>EquipOps v1.0</span>
          <span>企业内部系统 · 访问行为将被审计</span>
        </footer>
      </section>
    </main>
  );
}
