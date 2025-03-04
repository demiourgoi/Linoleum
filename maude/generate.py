import maude
import random
import sys

try:
    num_files = int(sys.argv[1])
except:
    print("Natural number expected as first argument")

try:
    out_name = sys.argv[2]
except:
    print("Path expected as second argument")

print(out_name)

def random_irreducible_term(t: maude.Term, max_iter=10000):
    while (True):
        options = list(t.apply(None))  # possible rewrite in one step with any rule
        if not options:  # irreducible
            return t
        t, subs, ctx, rule = random.choice(options)  # chooses a random rule

maude.init()
maude.load('./random.maude')

m = maude.getModule('TEST')
t = m.parseTerm('init')

final_states = [random_irreducible_term(t) for _ in range(num_files)]

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