/* implementation of candown.Renderer interface */
function render(s) {
//    return marked(s, {gfm: true});
    return "<html><body>"+marked(s, {gfm: true})+"</body></html>";
}