/* implementation of candown.Renderer interface 
 * global: marked 
 */
 var options = {gfm: true, mangle: false, headerIds: false}
function render(s) {
    return "<html><style>body { font-family: sans-serif }</style><body>"+marked.parse(s, options)+"</body></html>";
}