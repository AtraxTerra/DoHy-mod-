"""Traduit le bytecode (javap -c) d'un createBodyLayer de Blockbench en Java."""
import re, sys

def fmt(v):
    if isinstance(v, float):
        s = repr(v)
        if 'e' in s or 'E' in s:
            s = '%.6f' % v
        return s + 'f'
    return str(v)

def convert(path, indent='        '):
    lines = [l.strip() for l in open(path)]
    stack, local, out = [], {}, []
    counter = [0]
    def var():
        counter[0] += 1
        return 'p%d' % counter[0]
    for raw in lines:
        m = re.match(r'\d+: (\w+)\s*(.*)', raw)
        if not m:
            continue
        op, rest = m.group(1), m.group(2)
        comment = rest.split('//', 1)[1].strip() if '//' in rest else ''
        if op == 'new':
            stack.append(('new', comment.split()[-1]))
        elif op == 'dup':
            stack.append(stack[-1])
        elif op == 'invokespecial':
            if 'MeshDefinition."<init>"' in comment:
                stack.pop(); stack.pop(); stack.append('mesh')
            elif 'CubeDeformation."<init>":(F)V' in comment:
                arg = stack.pop(); stack.pop(); stack.pop()
                stack.append('new CubeDeformation(%s)' % fmt(arg))
            else:
                raise SystemExit('invokespecial inconnu: ' + comment)
        elif op.startswith('astore'):
            idx = op.split('_')[1] if '_' in op else rest.split()[0]
            value = stack.pop()
            if isinstance(value, tuple) and value[0] == 'expr':
                name = 'mesh' if value[1] == 'mesh' else var()
                if value[1] != 'mesh':
                    out.append('%sPartDefinition %s = %s;' % (indent, name, value[1]))
                local[idx] = name
            else:
                local[idx] = value if isinstance(value, str) else value
        elif op.startswith('aload'):
            idx = op.split('_')[1] if '_' in op else rest.split()[0]
            stack.append(local[idx])
        elif op in ('ldc', 'ldc_w'):
            if '// String ' in rest:
                stack.append('"%s"' % rest.split('// String ', 1)[1])
            elif '// float ' in rest:
                stack.append(float(rest.split('// float ', 1)[1].rstrip('f')))
            elif '// int ' in rest:
                stack.append(int(rest.split('// int ', 1)[1]))
            else:
                raise SystemExit('ldc inconnu: ' + rest)
        elif op.startswith('fconst_'):
            stack.append(float(op.split('_')[1]))
        elif op.startswith('iconst_'):
            v = op.split('_')[1]
            stack.append(-1 if v == 'm1' else int(v))
        elif op in ('bipush', 'sipush'):
            stack.append(int(rest.split()[0]))
        elif op == 'invokestatic':
            if 'CubeListBuilder.create' in comment:
                stack.append('CubeListBuilder.create()')
            elif 'PartPose.offsetAndRotation' in comment:
                args = [stack.pop() for _ in range(6)][::-1]
                stack.append('PartPose.offsetAndRotation(%s)' % ', '.join(fmt(a) for a in args))
            elif 'PartPose.offset' in comment:
                args = [stack.pop() for _ in range(3)][::-1]
                stack.append('PartPose.offset(%s)' % ', '.join(fmt(a) for a in args))
            elif 'LayerDefinition.create' in comment:
                h = stack.pop(); w = stack.pop(); stack.pop()
                out.append('%sreturn LayerDefinition.create(mesh, %s, %s);' % (indent, w, h))
            else:
                raise SystemExit('invokestatic inconnu: ' + comment)
        elif op == 'invokevirtual':
            if 'MeshDefinition.getRoot' in comment:
                stack.pop(); stack.append(('expr', 'mesh.getRoot()'))
            elif 'CubeListBuilder.texOffs' in comment:
                b = stack.pop(); a = stack.pop(); builder = stack.pop()
                stack.append('%s.texOffs(%s, %s)' % (builder, a, b))
            elif 'CubeListBuilder.addBox:(FFFFFFLnet' in comment:
                d = stack.pop(); args = [stack.pop() for _ in range(6)][::-1]; builder = stack.pop()
                stack.append('%s.addBox(%s, %s)' % (builder, ', '.join(fmt(a) for a in args), d))
            elif 'CubeListBuilder.mirror:()' in comment:
                builder = stack.pop(); stack.append('%s.mirror()' % builder)
            elif 'CubeListBuilder.mirror:(Z)' in comment:
                flag = stack.pop(); builder = stack.pop()
                stack.append('%s.mirror(%s)' % (builder, 'true' if flag else 'false'))
            elif 'PartDefinition.addOrReplaceChild' in comment:
                pose = stack.pop(); builder = stack.pop(); name = stack.pop(); parent = stack.pop()
                if isinstance(parent, tuple):
                    parent = parent[1]
                stack.append(('expr', '%s.addOrReplaceChild(%s, %s, %s)' % (parent, name, builder, pose)))
            else:
                raise SystemExit('invokevirtual inconnu: ' + comment)
        elif op == 'pop':
            value = stack.pop()
            if isinstance(value, tuple):
                out.append('%s%s;' % (indent, value[1]))
        elif op == 'areturn':
            pass
        elif op == 'getstatic':
            if 'CubeDeformation.NONE' in comment:
                stack.append('CubeDeformation.NONE')
            elif 'PartPose.ZERO' in comment:
                stack.append('PartPose.ZERO')
            else:
                raise SystemExit('getstatic inconnu: ' + comment)
        elif op in ('astore', 'aload'):
            pass
        else:
            raise SystemExit('op inconnu: %s %s' % (op, rest))
    return out

if __name__ == '__main__':
    for line in convert(sys.argv[1]):
        print(line)
