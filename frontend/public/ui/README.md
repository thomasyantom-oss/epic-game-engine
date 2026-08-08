Place sliced fantasy UI PNG assets in this directory.

Button 3-slice files:
- button_normal_left.png
- button_normal_center.png
- button_normal_right.png
- button_hover_left.png
- button_hover_center.png
- button_hover_right.png

Panel 9-slice files:
- panel_tl.png
- panel_t.png
- panel_tr.png
- panel_l.png
- panel_c.png
- panel_r.png
- panel_bl.png
- panel_b.png
- panel_br.png

The reusable components read these files through CSS variables in
`frontend/src/styles/variables.css`.

Adjust these variables if your slice dimensions differ:
- `--ui-button-height`
- `--ui-button-cap-width`
- `--ui-panel-corner-size`
- `--ui-panel-content-padding`
