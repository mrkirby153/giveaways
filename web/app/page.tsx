import Navbar from "@/components/Navbar";
import { getCurrentUser } from "@/modules/auth/helpers";
import Link from "next/link";

export const metadata = {
  title: "Giveaways",
  description: "A battle tested giveaway bot",
};

export default function Home() {
  return (
    <>
      <Navbar />
      <div className="h-screen pt-[3em]">
        <div className="flex flex-col justify-center">
          <p>This is some text</p>
        </div>
      </div>
    </>
  );
}
