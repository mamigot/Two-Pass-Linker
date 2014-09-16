Two-Pass-Linker
===============

Implementation of a two-pass linker in Java for a target machine that's word addressable with a memory of 600 words, each consisting of 4 decimal digits. The purpose of the program is relocate external addresses and resolve external differences.

The program accepts an input consisting of a series of modules wherein each is divided into definitions, use cases and the program text, and outputs a linked version of the modules with absolute addresses for the symbols as well as adjustments of the program text.
