# Shrink and optimise. main keeps -dontobfuscate for readable stack traces in crash screenshots; this
# branch's launcher (home_hook, pickers, the weather line) only fits under the 512 KB gate with names
# shortened, about 12 KB. A crash from this build needs app/build/outputs/mapping/release/mapping.txt.
