/* implementation of candown.Renderer interface 
 * global: marked 
 */
 var options = {gfm: true, mangle: false, headerIds: false}
function render(s) {
    return "<html><body>"+marked.parse(s, options)+"</body></html>";
}