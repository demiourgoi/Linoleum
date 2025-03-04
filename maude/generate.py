import maude
import random


def random_irreducible_term(t: maude.Term, max_iter=10000):
    while (True):
        options = list(t.apply(None))  # posibles reescrituras en un paso aplicando cualquier regla
        if not options:  # irreducible
            return t
        t, subs, ctx, rule = random.choice(options)  # escoge una reescritura al azar

maude.init()
maude.load('./random.maude')

m = maude.getModule('TEST')
t = m.parseTerm('init')
out_name = "jsons/py_solution"

final_states = [random_irreducible_term(t) for _ in range(10)]

symbols = m.getSymbols()
for symbol in symbols:
    if str(symbol) == "printTrace":
        printOp = symbol
        break

for counter, term in enumerate(final_states):
    name = out_name + str(counter) + ".jsonl"
    json = printOp.makeTerm([term])
    json.reduce()
    with open(name, 'w') as output:
        output.write(eval(str(json)))