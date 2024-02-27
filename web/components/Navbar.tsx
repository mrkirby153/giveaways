import { getCurrentUser } from "@/modules/auth/helpers";
import { getDisplayName } from "@/utils/userUtils";
import Link from "next/link";
import type { ReactNode } from "react";

type NavbarEntry = {
  name: string;
  href: string;
};

const leftEntries = [
  {
    name: "Home",
    href: "/",
  },
  {
    name: "Manage",
    href: "/manage",
  },
] satisfies NavbarEntry[];

function NavbarItem({ children }: { children: ReactNode }) {
  return <li className="inline px-2 py-3">{children}</li>;
}

function NavbarLink({ href, name }: { href: string; name: string }) {
  return (
    <Link
      href={href}
      className="text-gray-700 hover:text-black items-center hover:underline"
    >
      {name}
    </Link>
  );
}

async function LoginComponent() {
  const user = await getCurrentUser();
  if (user == null) {
    return (
      <NavbarItem>
        <NavbarLink href={"/oauth/login"} name={"Login"} />
      </NavbarItem>
    );
  } else {
    return (
      <NavbarItem>
        <NavbarLink href={"/oauth/logout"} name={getDisplayName(user)} />
      </NavbarItem>
    );
  }
}

export default async function Navbar() {
  const navbarLeftEntries = leftEntries.map((entry) => {
    return (
      <NavbarItem key={entry.href}>
        <NavbarLink href={entry.href} name={entry.name} />
      </NavbarItem>
    );
  });

  return (
    <>
      <nav className="bg-gray-300 flex fixed w-full">
        <ul className="flex">
          <li className="px-2 py-3 items-center">
            <Link href={"/"} className="text-black hover:text-gray-700">
              Giveaways
            </Link>
          </li>
          {navbarLeftEntries}
        </ul>
        <ul className="flex ml-auto">
          <LoginComponent />
        </ul>
      </nav>
    </>
  );
}
