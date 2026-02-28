(ns pulso.ui.terminal
  "ANSI-based terminal progress renderer.
   Runs a daemon thread that redraws progress bars at ~10 FPS
   using cursor-up + overwrite for flicker-free in-place updates.")

(def ^:private bar-width 30)
(def ^:private render-interval-ms 100)

(defn- format-number
  "Formats a number with thousand separators."
  [n]
  (let [fmt (java.text.NumberFormat/getIntegerInstance java.util.Locale/US)]
    (.format fmt (long n))))

(defn- format-duration
  "Formats seconds as M:SS."
  [secs]
  (let [m (quot secs 60)
        s (mod secs 60)]
    (format "%d:%02d" m s)))

(defn- progress-bar
  "Renders a progress bar string like [████████░░░░░░░░░░]"
  [pct width]
  (let [filled (int (* (/ pct 100.0) width))
        filled (min filled width)
        empty  (- width filled)]
    (str "["
         (apply str (repeat filled "\u2588"))
         (apply str (repeat empty "\u2591"))
         "]")))

(defn- render-type-line
  "Renders a single type progress line."
  [{:keys [type processed total pct]}]
  (let [label    (format "%-15s" (name type))
        bar      (progress-bar pct bar-width)
        pct-str  (format "%5.1f%%" pct)
        nums     (format "%s / %s"
                         (format "%,d" (long processed))
                         (format "%,d" (long total)))]
    (str "  " label " " bar " " pct-str "  " nums)))

(defn- render-frame
  "Renders a complete progress frame as a vector of lines."
  [snap filename]
  (let [{:keys [types overall-processed overall-total overall-pct
                rate eta-secs elapsed-secs]} snap
        header   (str "Pulso ETL - Processing " filename)
        divider  (apply str (repeat 74 "\u2500"))
        type-lines (mapv render-type-line types)
        summary  (format "  Overall: %5.1f%%  |  %s / %s  |  %s/s  |  ETA %ss  |  %s"
                         overall-pct
                         (format-number overall-processed)
                         (format-number overall-total)
                         (format-number rate)
                         eta-secs
                         (format-duration elapsed-secs))]
    (into [header divider] (concat type-lines [divider summary]))))

(defn- move-cursor-up [n]
  (str "\033[" n "A"))

(defn- clear-line []
  "\033[2K\r")

(defn- hide-cursor []
  "\033[?25l")

(defn- show-cursor []
  "\033[?25h")

(defn start-renderer!
  "Starts a background daemon thread that renders progress at ~10 FPS.
   Returns a map with :stop! fn to halt rendering.

   Arguments:
     state-atom  - atom created by progress/make-state
     snapshot-fn - function that takes @state-atom and returns a snapshot map
     filename    - display name for the file being processed"
  [state-atom snapshot-fn filename]
  (let [running   (volatile! true)
        lines-ref (volatile! 0)
        thread    (Thread.
                    (fn []
                      (print (hide-cursor))
                      (flush)
                      (try
                        (while @running
                          (let [snap  (snapshot-fn @state-atom)
                                lines (render-frame snap filename)
                                n     (count lines)]
                            ;; Move up to overwrite previous frame
                            (when (pos? @lines-ref)
                              (print (move-cursor-up @lines-ref)))
                            ;; Print each line, clearing to end
                            (doseq [line lines]
                              (print (str (clear-line) line "\n")))
                            (flush)
                            (vreset! lines-ref n)
                            (Thread/sleep render-interval-ms)))
                        (catch InterruptedException _)
                        (finally
                          (print (show-cursor))
                          (flush)))))]
    (.setDaemon thread true)
    (.setName thread "pulso-progress-renderer")
    (.start thread)
    {:stop! (fn []
              (vreset! running false)
              (.join thread 500)
              ;; Print final frame
              (let [snap  (snapshot-fn @state-atom)
                    lines (render-frame snap filename)]
                (when (pos? @lines-ref)
                  (print (move-cursor-up @lines-ref)))
                (doseq [line lines]
                  (print (str (clear-line) line "\n")))
                (flush)))}))
