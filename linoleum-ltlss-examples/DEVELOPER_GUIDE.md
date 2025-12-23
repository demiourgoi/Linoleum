# Developer guide

## How to

### How to identify how long it takes to generate an image in the Lotrbot example

Look for a span that:

- Is an image generation span, iff both of these conditions occour:
  - It executes a tool: attributes contain `["gen_ai.operation.name", "execute_tool"] `
  - The executed tool is `generate_image` (same function name as in lotrbot/main.py): attributes contain `["gen_ai.tool.name", "generate_image"]`
- The attributes "gen_ai.event.start_time" and "gen_ai.event.end_time" are the same as startTimeUnixNano and endTimeUnixNano (check https://www.epochconverter.com/). We can use any of them to compute the span duration

Example span

```
 < span("6015d7d2cb06c1810d715a35dae2167e/977a53c924deaca4") : Span | 
    traceId : "6015d7d2cb06c1810d715a35dae2167e",  spanId : "977a53c924deaca4", 
    parentSpanId : "a0ec2f841145b956",  
    name : "execute_tool generate_image", 
    startTimeUnixNano : 1766130650290576410, 
    endTimeUnixNano : 1766130652252886126, 
    attributes : 
        ["gen_ai.event.start_time", "2025-12-19T07:50:50.290592+00:00"]
        ["gen_ai.operation.name", "execute_tool"] 
        ["gen_ai.system", "strands-agents"] 
        ["gen_ai.tool.name", "generate_image"]
        ["gen_ai.tool.call.id", "eemIaR7ik"] 
        ["gen_ai.event.end_time", "2025-12-19T07:50:52.252808+00:00"] 
        ["gen_ai.tool.status", "success"],  
    events :  
        < event("6015d7d2cb06c1810d715a35dae2167e/977a53c924deaca4/0") : Event |  timeUnixNano : 1766130650290682528, name : "gen_ai.tool.message", attributes : ["role", "tool"] ["content", "{\'description\': \'A dark, shadowy figure with fiery eyes, wielding a whip of many thongs and a sword of fire, emerging from a dark, misty abyss, with a sense of power and terror.\'}"] ["id", "eemIaR7ik"] >   
        < event("6015d7d2cb06c1810d715a35dae2167e/977a53c924deaca4/1") : Event |  timeUnixNano : 1766130652252787002, name : "gen_ai.choice", attributes : ["message", "[{\'text\': \'Success generating image of A dark, shadowy figure with fiery eyes, wielding a whip of many thongs and a sword of fire, emerging from a dark, misty abyss, with a sense of power and terror. after 1.96 seconds\'}]"] ["id", "eemIaR7ik"] >  
> 
```