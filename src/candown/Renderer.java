/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package candown;

/**
 *
 * @author peterhull
 */
@FunctionalInterface
public interface Renderer {
    /**
     * Take the input string in markdown format and
     * convert to HTML
     * @param input the markdown string
     * @return the HTML output
     */
  String render(String input);  
}
