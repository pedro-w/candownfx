/* implementation of candown.Renderer interface */
function render(s) {
    return "<html><body>"+marked.parse(s, {gfm: true})+"</body></html>";
}