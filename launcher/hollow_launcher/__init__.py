"""Hollow Client launcher: installs Minecraft 1.21.11 + Fabric + Fabric API + Sodium + the Hollow Client mod into its
own folder (%APPDATA%\\.hollowclient), signs in with Microsoft and starts the game."""

APP_NAME = "Hollow Client"
VERSION = "1.1.0"
MC_VERSION = "1.21.11"
# The mod is built against this Fabric Loader; the launcher installs the newest stable loader that is at least this.
MIN_FABRIC_LOADER = "0.19.5"
