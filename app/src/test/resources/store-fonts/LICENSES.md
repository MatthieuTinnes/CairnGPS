# Store screenshot fonts

Used only by the store screenshot generator (`app/src/test/.../storescreenshots`), never shipped
in the APK. Static instances of the Latin subsets self-hosted by the CairnGPS website, converted
from WOFF2 with fontTools' `varLib.instancer`. In the italic, `b d h k l &` are mapped to the
font's own `.alt` glyphs (its small optical size forms): at headline sizes Fraunces swaps in
looped "wonky" shapes that make words like « il » or « la » hard to read.

| File | Family / instance | Licence |
| --- | --- | --- |
| `fraunces_medium.ttf` | [Fraunces](https://github.com/undercasetype/Fraunces), opsz 72, wght 500 — Copyright 2018 The Fraunces Project Authors | [SIL Open Font License 1.1](https://openfontlicense.org/open-font-license-official-text/) |
| `fraunces_medium_italic.ttf` | Fraunces Italic, opsz 72 | SIL Open Font License 1.1 |
| `manrope_regular.ttf`, `manrope_semibold.ttf` | [Manrope](https://github.com/sharanda/manrope), wght 400 / 600 — Copyright 2018 The Manrope Project Authors | SIL Open Font License 1.1 |
