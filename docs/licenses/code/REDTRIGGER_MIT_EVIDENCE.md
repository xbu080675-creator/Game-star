# RedTrigger MIT Evidence / REDMAGIC Shoulder Research

- Project: `RedTrigger`
- Author: Lucas Zampieri
- Repository: `https://github.com/zampierilucas/RedTrigger`
- License: MIT
- Copyright: Copyright (c) 2026 Lucas Zampieri
- Use in Game Star Box: platform research/reference for REDMAGIC/Nubia shoulder-trigger input semantics.

## Evidence used

The upstream project documents that REDMAGIC/Nubia capacitive shoulder sensors appear as Linux input/SAR devices whose names contain `nubia_tgk_aw_sar`, with:

- left shoulder: `KEY_F7` / scan code `0x41`;
- right shoulder: `KEY_F8` / scan code `0x42`.

It also demonstrates that a Shizuku shell-identity UserService can read the corresponding `/dev/input/eventN` nodes because normal Android application KeyEvent dispatch may not receive the raw trigger events.

## Deliberate non-use

Game Star Box does **not** import or reproduce RedTrigger's uinput injection path, virtual gamepad behavior, global remapping, permission-grant helper, or screen-touch injection behavior.

The 0.4.27 implementation is independently written as a narrower read-only adapter:

- internally fixed `/system/bin/getevent` only;
- self-detected and name-validated Nubia SAR nodes only;
- F7/F8 DOWN/UP only;
- calibration lifecycle only;
- no input injection or Settings writes.

## MIT license text

MIT License

Copyright (c) 2026 Lucas Zampieri

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
