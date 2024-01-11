/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package js;

import candown.Renderer;
//import com.oracle.truffle.js.runtime.JSContextOptions;

import java.io.IOException;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;

/**
 * @author peterhull
 */
public class Wrapper {


    private Context graalContext;

    public Wrapper() {
        init2();
    }
private void init2() {
        try {
            Engine graalEngine = Engine.newBuilder()
                    .option("engine.WarnInterpreterOnly", "false")
                    .build();
            Context context = Context.newBuilder("js")
                    .allowIO(IOAccess.NONE).engine(graalEngine).build();
            Source marked = Source.newBuilder("js", Wrapper.class.getResource("marked.min.js")).build();
            Source wrapper = Source.newBuilder("js", Wrapper.class.getResource("wrapper.js")).build();
            
            context.eval(marked);
            context.eval(wrapper);
            this.graalContext = context;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
}

    public Renderer getRenderer() {
        Value bindings = graalContext.getBindings("js").getMember("render");
        return (String input) -> bindings.execute(input).as(String.class);
    }
}
