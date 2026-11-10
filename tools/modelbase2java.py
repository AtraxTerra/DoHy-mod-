"""Traduit un modele 1.7.10 (ModelBase de Blockbench) en LayerDefinition 26.2.

Entree : la sortie de ``javap -p -c -constants`` de la classe du modele.
Sortie : le corps d'une methode ``createBodyLayer()`` sur la sortie standard.

Le constructeur d'un export « Modded Entity 1.7.10 » de Blockbench n'utilise
qu'une poignee d'instructions, toujours les memes :

- ``new ModelRenderer(this)`` puis ``putfield nom`` : une piece ;
- ``nom.func_78793_a(x, y, z)`` : son point de pivot (setRotationPoint) ;
- ``parent.func_78792_a(enfant)`` : la hierarchie (addChild) ;
- ``nom.cubeList.add(new ModelBox(nom, u, v, x, y, z, w, h, d, gonfle[, miroir]))`` ;
- ``setRotationAngle(nom, rx, ry, rz)`` : la rotation de repos ;
- ``field_78090_t`` / ``field_78089_u`` : la taille de la texture.

Le repere est le meme qu'en 26.2 (Y vers le bas, pieds a 24) : les cubes et
les pivots passent tels quels, sans retouche. Les racines sont les pieces que
``render`` (func_78088_a) dessine.

    python3 tools/modelbase2java.py Model.javap > corps.java
    python3 tools/modelbase2java.py Model.javap paquet NomGeometry > NomGeometry.java
"""
import re
import sys


def fmt(v):
    if isinstance(v, float):
        s = repr(v)
        if 'e' in s or 'E' in s:
            s = '%.6f' % v
        return s + 'f'
    return str(v)


class Part:
    def __init__(self, name):
        self.name = name
        self.pivot = (0.0, 0.0, 0.0)
        self.rot = (0.0, 0.0, 0.0)
        self.boxes = []
        self.children = []
        self.parent = None
        self.mirror = False


def methods(lines):
    """Decoupe la sortie de javap en {signature: [lignes de bytecode]}."""
    out, current = {}, None
    for line in lines:
        if re.match(r'^  \S', line):
            current = line.strip()
            out[current] = []
        elif current is not None:
            out[current].append(line.strip())
    return out


def run_ctor(code):
    parts, order = {}, []
    tex = [64, 32]
    stack = []
    for raw in code:
        m = re.match(r'\d+: (\w+)\s*(.*)', raw)
        if not m:
            continue
        op, rest = m.group(1), m.group(2)
        comment = rest.split('//', 1)[1].strip() if '//' in rest else ''
        if op == 'aload_0':
            stack.append('this')
        elif op == 'new':
            stack.append(('new', comment.split()[-1]))
        elif op == 'dup':
            stack.append(stack[-1])
        elif op.startswith('iconst_'):
            v = op.split('_')[1]
            stack.append(-1 if v == 'm1' else int(v))
        elif op in ('bipush', 'sipush'):
            stack.append(int(rest.split()[0]))
        elif op.startswith('fconst_'):
            stack.append(float(op.split('_')[1]))
        elif op in ('ldc', 'ldc_w'):
            if '// float ' in rest:
                stack.append(float(rest.split('// float ', 1)[1].rstrip('f')))
            elif '// int ' in rest:
                stack.append(int(rest.split('// int ', 1)[1]))
            else:
                raise SystemExit('ldc inconnu: ' + rest)
        elif op == 'getfield':
            field = comment.split('Field ', 1)[1].split(':')[0]
            owner = stack.pop()
            if field == 'net/minecraft/client/model/ModelRenderer.field_78804_l':
                stack.append(('cubes', owner))
            elif owner == 'this':
                stack.append(parts[field])
            else:
                raise SystemExit('getfield inconnu: ' + comment)
        elif op == 'putfield':
            field = comment.split('Field ', 1)[1].split(':')[0]
            value = stack.pop()
            owner = stack.pop()
            if field == 'field_78090_t':
                tex[0] = value
            elif field == 'field_78089_u':
                tex[1] = value
            elif field == 'net/minecraft/client/model/ModelRenderer.field_78809_i':
                owner.mirror = bool(value)
            elif isinstance(value, Part):
                value.name = field
                parts[field] = value
                order.append(field)
            elif owner == 'this':
                pass  # un champ du modele sans rapport avec la geometrie
            else:
                raise SystemExit('putfield inconnu: ' + comment)
        elif op == 'invokespecial':
            if 'ModelRenderer."<init>":(Lnet/minecraft/client/model/ModelBase;)V' in comment:
                stack.pop(); stack.pop(); stack.pop()
                stack.append(Part('?'))
            elif 'ModelBox."<init>"' in comment:
                desc = comment.split('(', 1)[1].split(')')[0]
                mirror = False
                if desc.endswith('Z'):
                    mirror = bool(stack.pop())
                inflate = stack.pop()
                d, h, w = stack.pop(), stack.pop(), stack.pop()
                z, y, x = stack.pop(), stack.pop(), stack.pop()
                v, u = stack.pop(), stack.pop()
                owner = stack.pop()
                stack.pop(); stack.pop()
                stack.append(('box', owner, u, v, x, y, z, w, h, d, inflate, mirror))
            elif '."<init>":()V' in comment:
                stack.pop()  # super()
            else:
                raise SystemExit('invokespecial inconnu: ' + comment)
        elif op == 'invokevirtual':
            if 'func_78793_a:(FFF)V' in comment:
                z, y, x = stack.pop(), stack.pop(), stack.pop()
                stack.pop().pivot = (x, y, z)
            elif 'func_78792_a:' in comment:
                child, parent = stack.pop(), stack.pop()
                child.parent = parent
                parent.children.append(child)
            elif 'setRotationAngle:' in comment:
                z, y, x = stack.pop(), stack.pop(), stack.pop()
                part = stack.pop()
                stack.pop()
                part.rot = (x, y, z)
            else:
                raise SystemExit('invokevirtual inconnu: ' + comment)
        elif op == 'invokeinterface' and 'List.add' in comment:
            box = stack.pop()
            target = stack.pop()
            assert target[0] == 'cubes' and box[0] == 'box'
            target[1].boxes.append(box[2:])
            stack.append(True)
        elif op == 'pop':
            stack.pop()
        elif op == 'return':
            break
        else:
            raise SystemExit('instruction inconnue: ' + raw)
    return parts, order, tex


def roots_of(render_code, parts):
    roots = []
    for raw in render_code:
        m = re.search(r'Field (\w+):Lnet/minecraft/client/model/ModelRenderer;', raw)
        if m and 'getfield' in raw and m.group(1) in parts and m.group(1) not in roots:
            roots.append(m.group(1))
    return [r for r in roots if parts[r].parent is None]


def emit(parts, roots, tex, indent='        '):
    out = ['%sMeshDefinition mesh = new MeshDefinition();' % indent,
           '%sPartDefinition root = mesh.getRoot();' % indent]

    def cubes(part):
        if not part.boxes:
            return 'CubeListBuilder.create()'
        s = 'CubeListBuilder.create()'
        for (u, v, x, y, z, w, h, d, inflate, mirror) in part.boxes:
            mirror = mirror or part.mirror
            s += '\n%s        .texOffs(%d, %d)' % (indent, u, v)
            if mirror:
                s += '.mirror()'
            s += '.addBox(%s, %s, %s, %s, %s, %s, new CubeDeformation(%s))' % (
                fmt(float(x)), fmt(float(y)), fmt(float(z)), fmt(float(w)), fmt(float(h)), fmt(float(d)),
                fmt(float(inflate)))
            if mirror:
                s += '.mirror(false)'
        return s

    def pose(part):
        px, py, pz = part.pivot
        rx, ry, rz = part.rot
        if rx == 0.0 and ry == 0.0 and rz == 0.0:
            return 'PartPose.offset(%s, %s, %s)' % (fmt(float(px)), fmt(float(py)), fmt(float(pz)))
        return 'PartPose.offsetAndRotation(%s, %s, %s, %s, %s, %s)' % tuple(
            fmt(float(v)) for v in (px, py, pz, rx, ry, rz))

    def walk(part, parent_var):
        var = part.name
        out.append('%sPartDefinition %s = %s.addOrReplaceChild("%s", %s,\n%s        %s);' % (
            indent, var, parent_var, part.name, cubes(part), indent, pose(part)))
        for child in part.children:
            walk(child, var)

    for name in roots:
        walk(parts[name], 'root')
    out.append('%sreturn LayerDefinition.create(mesh, %d, %d);' % (indent, tex[0], tex[1]))
    return '\n'.join(out)


def emit_index(parts, roots, indent='        '):
    """Le corps de index() : chaque piece par son nom, en suivant la hierarchie."""
    out = ['%sMap<String, ModelPart> parts = new HashMap<>();' % indent]

    def walk(part, parent_var):
        out.append('%sModelPart %s = %s.getChild("%s");' % (indent, part.name, parent_var, part.name))
        out.append('%sparts.put("%s", %s);' % (indent, part.name, part.name))
        for child in part.children:
            walk(child, part.name)

    for name in roots:
        walk(parts[name], 'root')
    out.append('%sreturn parts;' % indent)
    return '\n'.join(out)


CLASS = '''package %(package)s;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * La geometrie de {@code %(source)s} (PalaMod 10.3.0), traduite de son bytecode
 * par {@code tools/modelbase2java.py}, sans retouche des cubes. Ne pas modifier
 * a la main : regenerer.
 */
public final class %(name)s {

    private %(name)s() {
    }

    public static LayerDefinition createBodyLayer() {
%(body)s
    }

    /** Chaque piece du modele, par son nom d'origine. */
    public static Map<String, ModelPart> index(ModelPart root) {
%(index)s
    }
}
'''


def main(path, package=None, name=None):
    lines = [l.rstrip('\n') for l in open(path) if not l.startswith('Picked up')]
    ms = methods(lines)
    ctor_sig = next(k for k in ms if re.match(r'public [\w.$]+\(\);', k))
    ctor = ms[ctor_sig]
    render = next(v for k, v in ms.items() if 'func_78088_a(' in k)
    parts, order, tex = run_ctor(ctor)
    roots = roots_of(render, parts)
    if package and name:
        source = ctor_sig.split('(')[0].split()[-1].rsplit('.', 1)[-1]
        print(CLASS % {'package': package, 'name': name, 'source': source,
                       'body': emit(parts, roots, tex), 'index': emit_index(parts, roots)}, end='')
    else:
        print(emit(parts, roots, tex))
    # Bilan sur la sortie d'erreur : pieces, cubes, et l'etendue du modele
    boxes = [b for p in parts.values() for b in p.boxes]
    sys.stderr.write('%d pieces, %d cubes, racines %s, texture %dx%d\n' % (
        len(parts), len(boxes), roots, tex[0], tex[1]))


if __name__ == '__main__':
    # modelbase2java.py Model.javap [paquet NomDeClasse]
    main(*sys.argv[1:4])
