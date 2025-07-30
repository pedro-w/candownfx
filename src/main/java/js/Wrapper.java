/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package js;

import candown.Renderer;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.script.Compilable;
import javax.script.CompiledScript;

/**
 * @author peterhull
 */
public class Wrapper {

    private static final Renderer NULLRENDERER = (String input) -> "No Javascript Engine installed";
    private ScriptEngine engine;
    private Invocable invocable;
    private CompiledScript wrapper;
    private CompiledScript marked;

    public Wrapper() {
        init();
    }

    private void init() {
        ScriptEngineManager sem = new ScriptEngineManager();
        engine = sem.getEngineByExtension("js");
        if (engine instanceof Compilable compilable) {
            try {
                try (Reader rdr = new InputStreamReader(Wrapper.class.getResourceAsStream("marked.umd.js"))) {
                    marked = compilable.compile(rdr);
                } catch (IOException xep) {
                    // Shouldn't happen since the file is build into the JAR.
                    Logger.getLogger(Wrapper.class.getName()).log(Level.SEVERE, null, xep);
                }
                try (Reader rdr = new InputStreamReader(Wrapper.class.getResourceAsStream("wrapper.js"))) {
                    wrapper = compilable.compile(rdr);
                } catch (IOException xep) {
                    // Shouldn't happen since the file is built into the JAR.
                    Logger.getLogger(Wrapper.class.getName()).log(Level.SEVERE, null, xep);
                }

                marked.eval();
                wrapper.eval();
            } catch (ScriptException xep) {
                Logger.getLogger(Wrapper.class.getName()).log(Level.SEVERE, null, xep);
            }
        }
        if (engine instanceof Invocable inv) {
            invocable = inv;
        } else {
            invocable = null;
        }
    }

    public Renderer getRenderer() {
        return invocable == null ? NULLRENDERER : invocable.getInterface(Renderer.class);
    }
}
