package fr.atraxterra.dohy.client.ui;

/**
 * Le temps des animations du kit : en secondes reelles, independant des images par
 * seconde, et quelques courbes d'acceleration.
 */
public final class Anim {

    private Anim() {
    }

    /** Le temps courant, en secondes. */
    public static double now() {
        return System.nanoTime() / 1.0e9d;
    }

    /** L'avancement de 0 a 1 d'une animation commencee a {@code start}, de {@code duration} secondes. */
    public static float progress(double start, double duration) {
        if (duration <= 0) {
            return 1.0f;
        }
        return (float) Math.max(0.0d, Math.min(1.0d, (now() - start) / duration));
    }

    /** Depart vif, arrivee douce. */
    public static float easeOut(float t) {
        float c = Math.max(0.0f, Math.min(1.0f, t));
        float inverse = 1.0f - c;
        return 1.0f - inverse * inverse * inverse;
    }

    /** Doux aux deux bouts. */
    public static float easeInOut(float t) {
        float c = Math.max(0.0f, Math.min(1.0f, t));
        return c < 0.5f ? 4.0f * c * c * c : 1.0f - (float) Math.pow(-2.0f * c + 2.0f, 3) / 2.0f;
    }

    /** Une pulsation lente entre 0 et 1 (badges « pret »). */
    public static float pulse(double period) {
        return (float) (0.5d + 0.5d * Math.sin(now() * Math.PI * 2.0d / period));
    }

    /**
     * Une valeur qui suit sa cible en douceur : survol des cartes, onglet actif.
     * Chaque element garde la sienne.
     */
    public static final class Value {
        private float value;
        private double last = -1.0d;
        private final float speed;

        public Value(float initial, float speed) {
            this.value = initial;
            this.speed = speed;
        }

        /** Avance vers {@code target} selon le temps ecoule depuis le dernier appel, et rend la valeur. */
        public float toward(float target) {
            double time = now();
            if (last < 0) {
                last = time;
            }
            double delta = Math.min(0.25d, time - last);
            last = time;
            value += (float) ((target - value) * (1.0d - Math.exp(-delta * speed)));
            if (Math.abs(target - value) < 0.002f) {
                value = target;
            }
            return value;
        }

        public float get() {
            return value;
        }

        public void set(float value) {
            this.value = value;
        }
    }
}
