// SmartPark V1 ceiling sensor enclosure — parametric prototype
// Render/export: set part = "base", "lid", or "assembly".
// Dimensions are a starting envelope; measure the actual camera carrier before printing.
$fn = 48;
part = "assembly";

outer_w = 100;
outer_d = 80;
base_h = 36;
lid_h = 9;
wall = 2.4;
floor_t = 2.4;
corner_r = 7;
lid_clearance = 0.35;

camera_x = outer_w / 2;
camera_y = outer_d / 2;
camera_aperture_d = 22;   // Set to lens barrel + clearance after measuring.
window_seat_d = 28;       // Clear optical window retained with adhesive/gasket.
window_seat_depth = 1.2;

board_w = 29;             // Carrier envelope, not bare XIAO PCB dimensions.
board_d = 26;
board_clip_h = 7;
board_clip_t = 1.8;
buck_x = 73;
buck_y = 40;
buck_w = 22;
buck_d = 30;

mount_hole_d = 4.5;       // M4 ceiling screws.
mount_slot_l = 10;
mount_tab_w = 15;
mount_tab_d = 12;

module rounded_prism(w, d, h, r) {
  hull() {
    for (x = [r, w-r])
      for (y = [r, d-r])
        translate([x, y, 0]) cylinder(h=h, r=r);
  }
}

module screw_boss(x, y) {
  translate([x, y, floor_t])
    difference() {
      cylinder(h=base_h-floor_t-2, d=8);
      translate([0,0,-0.1]) cylinder(h=base_h+1, d=2.8);
    }
}

module board_clip(x, y, sx, sy) {
  translate([x, y, floor_t])
    cube([sx, sy, board_clip_h]);
}

module base_shell() {
  difference() {
    union() {
      rounded_prism(outer_w, outer_d, base_h, corner_r);
      // Two external mounting ears; slots permit small alignment adjustments.
      translate([outer_w/2-mount_tab_w/2, -mount_tab_d+2, 0])
        cube([mount_tab_w, mount_tab_d, 4]);
      translate([outer_w/2-mount_tab_w/2, outer_d-2, 0])
        cube([mount_tab_w, mount_tab_d, 4]);
      screw_boss(10,10);
      screw_boss(outer_w-10,10);
      screw_boss(10,outer_d-10);
      screw_boss(outer_w-10,outer_d-10);
      // Camera carrier corner ledges (adjust to the measured carrier).
      board_clip(camera_x-board_w/2-2, camera_y-board_d/2-2, 4, 5);
      board_clip(camera_x+board_w/2-2, camera_y-board_d/2-2, 4, 5);
      board_clip(camera_x-board_w/2-2, camera_y+board_d/2-3, 4, 5);
      board_clip(camera_x+board_w/2-2, camera_y+board_d/2-3, 4, 5);
      // Buck-converter rails.
      board_clip(buck_x-buck_w/2-2, buck_y-buck_d/2, 2, buck_d);
      board_clip(buck_x+buck_w/2, buck_y-buck_d/2, 2, buck_d);
    }
    // Open-top electronics cavity.
    translate([wall, wall, floor_t])
      rounded_prism(outer_w-2*wall, outer_d-2*wall, base_h, corner_r-wall);
    // Lens aperture and optical-window seat from the outside face.
    translate([camera_x,camera_y,-0.1])
      cylinder(h=floor_t+0.3, d=camera_aperture_d);
    translate([camera_x,camera_y,-0.01])
      cylinder(h=window_seat_depth, d=window_seat_d);
    // Rear cable-gland opening.
    translate([outer_w-1, outer_d/2, 18])
      rotate([0,90,0]) cylinder(h=wall+2, d=8);
    // Ceiling mounting slots.
    for (x=[outer_w/2]) {
      for (y=[-mount_tab_d/2+2, outer_d+mount_tab_d/2-2]) {
        translate([x,y,-0.1])
          hull() {
            translate([-mount_slot_l/2,0,0]) cylinder(h=4.2,d=mount_hole_d);
            translate([ mount_slot_l/2,0,0]) cylinder(h=4.2,d=mount_hole_d);
          }
      }
    }
    // Lid screw holes.
    for (x=[10,outer_w-10])
      for (y=[10,outer_d-10])
        translate([x,y,-0.1]) cylinder(h=base_h+1,d=2.6);
  }
}

module lid() {
  difference() {
    rounded_prism(outer_w, outer_d, lid_h, corner_r);
    translate([wall+lid_clearance, wall+lid_clearance, 1.2])
      rounded_prism(outer_w-2*(wall+lid_clearance),
                    outer_d-2*(wall+lid_clearance),
                    lid_h, corner_r-wall);
    for (x=[10,outer_w-10])
      for (y=[10,outer_d-10])
        translate([x,y,-0.1]) cylinder(h=lid_h+1,d=3.2);
  }
}

module assembly() {
  color("gainsboro") base_shell();
  translate([0,0,base_h+0.6]) color("white") lid();
  // The clear optical window is intentionally a separate purchased/cut part.
  translate([camera_x,camera_y,0.25])
    color([0.55,0.75,0.9,0.55]) cylinder(h=1.0,d=window_seat_d-0.4);
}

if (part == "base") base_shell();
else if (part == "lid") lid();
else assembly();
