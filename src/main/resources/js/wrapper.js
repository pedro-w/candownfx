/* implementation of candown.Renderer interface 
 * global: marked 
 */
function render(s) {
    return "<html><body>"+marked.parse(s, {gfm: true})+"</body></html>";
}