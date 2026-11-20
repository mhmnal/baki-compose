# Workflow

Two speeds, one final gate. The fast loop is for building; the gate is for shipping.

## Fast loop

Use the warm Gradle daemon, incremental compilation and the build cache.

```bash
./scripts/fastcheck.sh app.baki.PhoneShot
./scripts/fastcheck.sh app.baki.WeekCloseTest
```

Rules:

- Do **not** pass `--no-daemon`.
- Do **not** pass `--no-build-cache`.
- Do **not** delete `composeApp/build` or the Kotlin compile cache unless a real cache failure occurred.
- Do **not** build/sign a release APK for a UI iteration.
- Do **not** reinstall the desktop app for a visual check; use `PhoneShot` / App Preview.
- Run only the affected tests.
- For Android iteration, use a debug install; reserve the verification lane for the gate.

## Release gate

Once the feature is complete:

```bash
./scripts/gate.sh
```

That runs:

1. full JVM suite
2. Android verification lane
3. unsigned release build

Then, and only when the user asks to ship:

```bash
./scripts/release.sh 0.50.3
```

Signing, publishing and installing are explicit final steps.

## When a clean build is justified

Only on a real incremental-compilation failure, for example:

- `Incremental compilation failed`
- `Storage corrupted`
- `Could not delete ... classes`
- `NoSuchFileException ... in-progress-results`
- `EOFException` while writing `build/test-results`

Stop the daemon, remove the single failed cache directory, and retry. Do not clean the whole project by habit.

## Preview is still required

After a UI change, render and inspect the affected frames:

```bash
./scripts/fastcheck.sh app.baki.PhoneShot
```

Then inspect `composeApp/build/preview/{light,dark}/{phone,desk}/*.png`. A fast compile is not visual proof.

## Definition of done

- affected tests pass in the fast loop
- affected previews/goldens inspected
- `./scripts/gate.sh` passes for a shippable feature
- signed APK installed only after explicit instruction
- commit the source, tests and golden changes together
