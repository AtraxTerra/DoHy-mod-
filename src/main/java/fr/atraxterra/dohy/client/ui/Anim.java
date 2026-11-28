package fr.atraxterra.dohy.client.ui;

/**
 * Le temps des animations du kit : en secondes reelles, independant des images par
 * seconde, les courbes d'acceleration de Robert Penner ({@link Ease}, celles du moteur
 * d'animation de Zephyr-UI) et l'interpolation d'une valeur dans le temps
 * ({@link Tween}).
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

    /**
     * Les courbes d'acceleration de Robert Penner, celles des {@code TweenEquations} de
     * Zephyr-UI : lineaire, puis quadratique, cubique, quartique, quintique, sinus,
     * exponentielle, circulaire, avec recul, elastique et rebond, chacune en entree
     * ({@code IN}, depart lent), en sortie ({@code OUT}, arrivee douce) et aux deux bouts.
     */
    public enum Ease {
        LINEAR,
        QUAD_IN, QUAD_OUT, QUAD_IN_OUT,
        CUBIC_IN, CUBIC_OUT, CUBIC_IN_OUT,
        QUART_IN, QUART_OUT, QUART_IN_OUT,
        QUINT_IN, QUINT_OUT, QUINT_IN_OUT,
        SINE_IN, SINE_OUT, SINE_IN_OUT,
        EXPO_IN, EXPO_OUT, EXPO_IN_OUT,
        CIRC_IN, CIRC_OUT, CIRC_IN_OUT,
        BACK_IN, BACK_OUT, BACK_IN_OUT,
        ELASTIC_IN, ELASTIC_OUT, ELASTIC_IN_OUT,
        BOUNCE_IN, BOUNCE_OUT, BOUNCE_IN_OUT;

        /** Le recul des courbes {@code BACK} (la valeur classique de Penner). */
        private static final double OVERSHOOT = 1.70158d;

        /** La courbe en {@code t} (0 a 1, borne) ; vaut 0 en 0 et 1 en 1. */
        public float apply(float t) {
            double x = Math.max(0.0d, Math.min(1.0d, t));
            return (float) switch (this) {
                case LINEAR -> x;
                case QUAD_IN -> x * x;
                case QUAD_OUT -> 1.0d - (1.0d - x) * (1.0d - x);
                case QUAD_IN_OUT -> x < 0.5d ? 2.0d * x * x : 1.0d - Math.pow(-2.0d * x + 2.0d, 2) / 2.0d;
                case CUBIC_IN -> x * x * x;
                case CUBIC_OUT -> 1.0d - Math.pow(1.0d - x, 3);
                case CUBIC_IN_OUT -> x < 0.5d ? 4.0d * x * x * x : 1.0d - Math.pow(-2.0d * x + 2.0d, 3) / 2.0d;
                case QUART_IN -> Math.pow(x, 4);
                case QUART_OUT -> 1.0d - Math.pow(1.0d - x, 4);
                case QUART_IN_OUT -> x < 0.5d ? 8.0d * Math.pow(x, 4) : 1.0d - Math.pow(-2.0d * x + 2.0d, 4) / 2.0d;
                case QUINT_IN -> Math.pow(x, 5);
                case QUINT_OUT -> 1.0d - Math.pow(1.0d - x, 5);
                case QUINT_IN_OUT -> x < 0.5d ? 16.0d * Math.pow(x, 5) : 1.0d - Math.pow(-2.0d * x + 2.0d, 5) / 2.0d;
                case SINE_IN -> 1.0d - Math.cos(x * Math.PI / 2.0d);
                case SINE_OUT -> Math.sin(x * Math.PI / 2.0d);
                case SINE_IN_OUT -> -(Math.cos(Math.PI * x) - 1.0d) / 2.0d;
                case EXPO_IN -> x == 0.0d ? 0.0d : Math.pow(2.0d, 10.0d * x - 10.0d);
                case EXPO_OUT -> x == 1.0d ? 1.0d : 1.0d - Math.pow(2.0d, -10.0d * x);
                case EXPO_IN_OUT -> x == 0.0d || x == 1.0d ? x
                        : x < 0.5d ? Math.pow(2.0d, 20.0d * x - 10.0d) / 2.0d
                        : (2.0d - Math.pow(2.0d, -20.0d * x + 10.0d)) / 2.0d;
                case CIRC_IN -> 1.0d - Math.sqrt(1.0d - x * x);
                case CIRC_OUT -> Math.sqrt(1.0d - (x - 1.0d) * (x - 1.0d));
                case CIRC_IN_OUT -> x < 0.5d ? (1.0d - Math.sqrt(1.0d - 4.0d * x * x)) / 2.0d
                        : (Math.sqrt(1.0d - Math.pow(-2.0d * x + 2.0d, 2)) + 1.0d) / 2.0d;
                case BACK_IN -> (OVERSHOOT + 1.0d) * x * x * x - OVERSHOOT * x * x;
                case BACK_OUT -> 1.0d + (OVERSHOOT + 1.0d) * Math.pow(x - 1.0d, 3) + OVERSHOOT * Math.pow(x - 1.0d, 2);
                case BACK_IN_OUT -> backInOut(x);
                case ELASTIC_IN -> x == 0.0d || x == 1.0d ? x
                        : -Math.pow(2.0d, 10.0d * x - 10.0d) * Math.sin((x * 10.0d - 10.75d) * (2.0d * Math.PI / 3.0d));
                case ELASTIC_OUT -> x == 0.0d || x == 1.0d ? x
                        : Math.pow(2.0d, -10.0d * x) * Math.sin((x * 10.0d - 0.75d) * (2.0d * Math.PI / 3.0d)) + 1.0d;
                case ELASTIC_IN_OUT -> elasticInOut(x);
                case BOUNCE_IN -> 1.0d - bounceOut(1.0d - x);
                case BOUNCE_OUT -> bounceOut(x);
                case BOUNCE_IN_OUT -> x < 0.5d ? (1.0d - bounceOut(1.0d - 2.0d * x)) / 2.0d
                        : (1.0d + bounceOut(2.0d * x - 1.0d)) / 2.0d;
            };
        }

        private static double backInOut(double x) {
            double c = OVERSHOOT * 1.525d;
            return x < 0.5d ? Math.pow(2.0d * x, 2) * ((c + 1.0d) * 2.0d * x - c) / 2.0d
                    : (Math.pow(2.0d * x - 2.0d, 2) * ((c + 1.0d) * (x * 2.0d - 2.0d) + c) + 2.0d) / 2.0d;
        }

        private static double elasticInOut(double x) {
            if (x == 0.0d || x == 1.0d) {
                return x;
            }
            double c = 2.0d * Math.PI / 4.5d;
            return x < 0.5d ? -(Math.pow(2.0d, 20.0d * x - 10.0d) * Math.sin((20.0d * x - 11.125d) * c)) / 2.0d
                    : Math.pow(2.0d, -20.0d * x + 10.0d) * Math.sin((20.0d * x - 11.125d) * c) / 2.0d + 1.0d;
        }

        private static double bounceOut(double x) {
            double n = 7.5625d;
            double d = 2.75d;
            if (x < 1.0d / d) {
                return n * x * x;
            }
            if (x < 2.0d / d) {
                double y = x - 1.5d / d;
                return n * y * y + 0.75d;
            }
            if (x < 2.5d / d) {
                double y = x - 2.25d / d;
                return n * y * y + 0.9375d;
            }
            double y = x - 2.625d / d;
            return n * y * y + 0.984375d;
        }
    }

    /**
     * Une valeur qui va de {@code from} a {@code to} en {@code duration} secondes, sur une
     * courbe, apres un delai : l'entree d'un panneau, un compteur qui monte.
     */
    public static final class Tween {
        private final float from;
        private final float to;
        private final double duration;
        private final double delay;
        private final Ease ease;
        private double start;

        public Tween(float from, float to, double duration, Ease ease) {
            this(from, to, duration, 0.0d, ease);
        }

        public Tween(float from, float to, double duration, double delay, Ease ease) {
            this.from = from;
            this.to = to;
            this.duration = duration;
            this.delay = delay;
            this.ease = ease;
            this.start = now();
        }

        /** Repart de zero. */
        public void restart() {
            start = now();
        }

        /** La valeur du moment. */
        public float value() {
            float t = progress(start + delay, duration);
            return from + (to - from) * ease.apply(t);
        }

        /** Le delai et la duree sont passes. */
        public boolean done() {
            return now() - start >= delay + duration;
        }
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
