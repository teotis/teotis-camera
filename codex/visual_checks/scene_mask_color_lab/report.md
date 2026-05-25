# Scene Mask Color Lab Prototype

This is an offline PIL prototype. It uses hand-drawn approximate masks, not a real ML model.
The purpose is to compare global color rendering with mask-aware subject protection.

## day_portrait

- Global avg delta subject/background: 15.9 / 14.0
- Mask-aware avg delta subject/background: 5.2 / 13.2

## night_portrait

- Global avg delta subject/background: 17.5 / 14.5
- Mask-aware avg delta subject/background: 5.9 / 14.0
