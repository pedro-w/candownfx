/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package js;

import candown.Renderer;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

/**
 *
 * @author peterhull
 */
public class Wrapper {

    private final ScriptEngine engine;
    private final Invocable invocable;

    public Wrapper() {
        ScriptEngineManager sem = new ScriptEngineManager();
        engine = sem.getEngineByExtension("js");
        if (engine != null) {
            try (Reader rdr = new InputStreamReader(Wrapper.class.getResourceAsStream("marked.umd.js"))) {
                engine.eval(rdr);
            } catch (IOException | ScriptException xep) {
                // Shouldn't happen since the file is build into the JAR.
                Logger.getLogger(Wrapper.class.getName()).log(Level.SEVERE, null, xep);
            }
            try (Reader rdr = new InputStreamReader(Wrapper.class.getResourceAsStream("wrapper.js"))) {
                engine.eval(rdr);
            } catch (IOException | ScriptException xep) {
                // Shouldn't happen since the file is built into the JAR.
                Logger.getLogger(Wrapper.class.getName()).log(Level.SEVERE, null, xep);
            }
            invocable = (Invocable) engine;
        } else {
            invocable = null;
        }
    }

    private static final Renderer NULLRENDERER = (String input) -> "No Javascript Engine installed";

    public Renderer getRenderer() {
        return invocable == null ? NULLRENDERER : invocable.getInterface(Renderer.class);
    }
}
