import { AuthPage } from "../components/AuthPage";

interface LoginPageProps {
  searchParams: Promise<{
    registered?: string;
    username?: string;
  }>;
}

export default async function LoginPage({ searchParams }: LoginPageProps) {
  const params = await searchParams;
  return (
    <AuthPage
      mode="login"
      initialUsername={params.username || ""}
      registrationSuccess={params.registered === "1"}
    />
  );
}
