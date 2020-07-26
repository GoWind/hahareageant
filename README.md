# Breakitdown

Break it down is a TODO app, with a twist

1. Keep track of your tasks using task lists
2. You can have multiple task lists at once.
3. Too big a task ? No problem. Break it down into subtasks !

### **This App is feature complete**

## Motivation

1. I wanted to learn front end development
2. I wanted to learn Clojurescript
3. I do not like Javascript, To be honest
4. I wanted a TODO app that I would love to use

Existing TODO apps weren't cutting it for me. 
As people who have had checklists, you can very well attest to the fact that
sometimes, a single task has to be broken down into multiple sub-tasks.
Tasks are dynamic and can be ever changing

TODO:

2. Re-order tasks (WONTFIX, re-ordering isn't that important to me)


## Setup

To get an interactive development environment run:

    lein figwheel

and open your browser at [localhost:3449](http://localhost:3449/).
This will auto compile and send all changes to the browser without the
need to reload. After the compilation process is complete, you will
get a Browser Connected REPL. An easy way to try it is:

    (js/alert "Am I connected?")

and you should see an alert in the browser window.

To clean all compiled files:

    lein clean

To create a production build run:

    lein do clean, cljsbuild once min

And open your browser in `resources/public/index.html`. You will not
get live reloading, nor a REPL. 

## License

Copyright © 2020 Govindarajan (govindnagarajan@outlook.com)

