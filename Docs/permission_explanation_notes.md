# User Permission Explanations – Notes for Future Implementation

## Context
The app requests multiple permissions on startup, including:
- ACCESS_FINE_LOCATION
- ACCESS_COARSE_LOCATION
- ACCESS_BACKGROUND_LOCATION

Currently, the app requests these in two separate steps, following Android’s guidelines.

## TODO: Add User-Facing Explanation UI

Before or alongside permission prompts, we should explain:

### Why We Need Location Permissions
- **Foreground Location (Fine/Coarse):**  
  Required to detect where the car is parked when a RingGo session starts.

- **Background Location:**  
  Enables the app to detect when the user drives away from the parking zone *even when the app is not in the foreground*, so we can trigger a reminder.

### Suggested Text (Before Prompt)
> “To help you avoid parking overcharges, this app needs access to your location.  
We only use it locally to track where you parked and remind you if you forget to end your session.”

### Implementation Ideas
- Use a `Dialog` or `AlertDialog` in Jetpack Compose before requesting permissions.
- Trigger this dialog only the first time the app runs.
- Link to a local Privacy Policy if required for Play Store review.

## Optional Future Improvements
- Add a "Why this permission?" explanation next to each permission block in the app UI.
- Direct users to Settings if they deny permissions.

