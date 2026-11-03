# KinoTV

Android TV application with a custom, remote-first movie and series interface inspired by the MyMovie-style layout.

## Status

This repository is the new KinoTV app. The initial project scaffold and custom TV interface are being added. The catalogue adapter targets `https://1.kinogoline.net/`; its selectors must be verified against the live site before catalogue and playback support can be considered complete.

## Build

The project uses Gradle and GitHub Actions to build a debug APK on pushes and pull requests. A release APK can be configured with a dedicated signing key stored in GitHub Actions secrets.

## Security

Do not commit signing keys, passwords, tokens, or private credentials.