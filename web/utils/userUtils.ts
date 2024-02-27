import { User } from "@/modules/auth/oauth";

export function getDisplayName(user: User) {
  if (user.global_name) {
    return user.global_name;
  }
  if (user.discriminator != "0") {
    return `${user.username}#${user.discriminator}`;
  }
  return user.username;
}
