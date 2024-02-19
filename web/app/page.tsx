import { getCurrentUser } from "@/modules/auth/helpers";
import Link from "next/link";

export default function Home() {
  return (
    <div>
      <h1>Home</h1>
      <CurrentUser />
    </div>
  );
}

async function CurrentUser() {
  let user = await getCurrentUser();

  if (user) {
    console.log(user);
    return (
      <>
        <div>
          <pre>{user.id}</pre>
        </div>
        <Link href="/oauth/logout">Logout</Link>
      </>
    );
  } else {
    return (
      <>
        <Link href="/oauth/login">Login</Link>
      </>
    );
  }
}
