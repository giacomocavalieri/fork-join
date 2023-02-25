# Fork-join DSL

A bare bone fork-join DSL in Scala developed with [@vitlinda](https://github.com/vitlinda) as a funny exercise. It uses the _"free operational monad"_ encoding to get a nice syntax for the DSL for _free_ (pun intended).

Our goal was to create a basic DSL for _describing_ distributed programs and later give them semantics by _interpreting_ them.
This is 100% just an experiment and far from being complete. The end goal would be to have a minimal library that would allow one to implement a fork-reduce distributed computation.

Future work:

- Create an interpreter to draw a graph of the distributed computation ([à la Unison Remote Ability](https://twitter.com/r_l_mark/status/1620886503542120448?s=61&t=7PRJ4XOlaZns-MumtcG-zQ))
- Create an interpreter that can simulate network failure (for example given a chance the failure could occur) (`Fiber.getResult` should return an `Either` to model possible failure, so this is a change we would have to implement)
