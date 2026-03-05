"use client";

import React from "react";
import { signOut } from "next-auth/react";
import { LogOut, User, ChevronDown } from "lucide-react";
import Image from "next/image";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Button } from "@/components/ui/button";

interface HeaderProps {
  userName: string | null;
  userEmail: string | null;
  userImage: string | null;
  pageTitle: string;
}

export function Header({ userName, userEmail, userImage, pageTitle }: HeaderProps) {
  return (
    <header className="flex h-16 items-center justify-between border-b bg-white px-6" role="banner">
      <h1 className="text-lg font-semibold text-gray-900">{pageTitle}</h1>

      <div className="flex items-center gap-4">
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button
              variant="ghost"
              className="flex items-center gap-2 px-2"
              aria-label="User menu"
            >
              {userImage ? (
                <Image
                  src={userImage}
                  alt={userName ?? "User avatar"}
                  width={32}
                  height={32}
                  className="rounded-full"
                />
              ) : (
                <div
                  className="flex h-8 w-8 items-center justify-center rounded-full bg-blue-100"
                  aria-hidden="true"
                >
                  <User className="h-4 w-4 text-blue-600" />
                </div>
              )}
              <span className="hidden text-sm font-medium text-gray-700 sm:inline">
                {userName ?? userEmail ?? "User"}
              </span>
              <ChevronDown className="h-4 w-4 text-gray-500" aria-hidden="true" />
            </Button>
          </DropdownMenuTrigger>

          <DropdownMenuContent align="end" className="w-56">
            <DropdownMenuLabel>
              <div className="flex flex-col space-y-1">
                {userName && (
                  <p className="text-sm font-medium leading-none">{userName}</p>
                )}
                {userEmail && (
                  <p className="text-xs leading-none text-muted-foreground">{userEmail}</p>
                )}
              </div>
            </DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuItem
              onClick={() => signOut({ callbackUrl: "/login" })}
              className="cursor-pointer text-red-600 focus:text-red-600"
            >
              <LogOut className="mr-2 h-4 w-4" aria-hidden="true" />
              Sign out
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </header>
  );
}
