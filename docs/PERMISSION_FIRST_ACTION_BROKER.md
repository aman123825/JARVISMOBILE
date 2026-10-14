# Permission-first action broker test plan

This feature keeps low-risk navigation direct while asking before actions that have persistent or external effects.

## Expected behavior

1. `Open WhatsApp` — launches immediately if the app is installed and launchable.
2. `Open Settings` — launches immediately.
3. `Send Rahul a message saying I will be 10 minutes late` — shows the approval dialog; after approval, opens an SMS draft. It never taps Send.
4. `Remind me to submit the assignment tomorrow at 9am` — shows the approval dialog; after approval, opens a Calendar event draft. The user chooses date/time and saves in Calendar.
5. `Start 90 minute ultradian deep work block on STEM` — shows the approval dialog. Only an explicit approval can reach the focus-lock executor.
6. `Explain what deep work means` — remains a normal conversation and must not lock the device.

## Manual trial checklist

- [ ] Install the debug APK on a personal test device.
- [ ] Verify open-app commands do not show the action approval dialog.
- [ ] Verify cancellation prevents the sensitive action from running.
- [ ] Verify SMS opens a populated draft and does not send.
- [ ] Verify Calendar opens a draft and does not save by itself.
- [ ] Verify Focus/Monk Mode only executes after approval.
- [ ] Review Android permission prompts before granting them.

## Known scope

This is the first action-broker milestone, not universal device control. Unimplemented action types should fall back to conversation or ask for clarification; they must not be guessed or executed dynamically.
