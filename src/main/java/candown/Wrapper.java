/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package candown;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
/**
 *
 * @author peterhull
 */
public class Wrapper {
  
    public Renderer getRenderer() {
        Parser parser = Parser.builder().build();
        HtmlRenderer renderer = HtmlRenderer.builder().build();
        return (String input) -> {
            Node parsed = parser.parse(input);
            return renderer.render(parsed);
        };
    }
}
