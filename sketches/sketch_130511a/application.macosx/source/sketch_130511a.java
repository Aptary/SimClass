import processing.core.*; 
import processing.data.*; 
import processing.event.*; 
import processing.opengl.*; 

import java.util.HashMap; 
import java.util.ArrayList; 
import java.io.File; 
import java.io.BufferedReader; 
import java.io.PrintWriter; 
import java.io.InputStream; 
import java.io.OutputStream; 
import java.io.IOException; 

public class sketch_130511a extends PApplet {

//-*****************************************************************************
// Copyright (c) 2011-2013 Christopher Jon Horvath. All rights reserved.
//-*****************************************************************************

//-*****************************************************************************
//-*****************************************************************************
// GLOBAL VARIABLES (SIMULATION PARAMETERS)
//-*****************************************************************************
//-*****************************************************************************

// Grid resolution per side. Rectangular
int NX = 128;
int NY = 128;

// The size of the sim, in "world" units.
float LX = 100.0f;

// Size, in "world" units, of a grid cell.
// Our cells are uniform (square) so DX & DY are the same.
float DXY = LX / ( float )NX;

// Y size, keeping square cells.
float LY = DXY * ( float )NY;

// The size of each grid cell, in pixels.
// This is for drawing
int CellPixels = 4;

// The rate at which we inject density
// into the grid by painting with the
// mouse.
float EmissionRate = 1.0f;
float EmissionRadius = 20.0f;

// The rate at which density
// diffuses (dissipates)
float D_viscosity = 0.00001f;

// The rate at which velocity
// dissipates  
float V_viscosity = 0.00001f;

// The rate at which density decays.
float D_damp = 0.01f;

// The rate at which velocity decays.
float V_damp = 0.0001f;

// Our time step 
float DT = 1.0f; 

// A scale on input velocity 
float Vscale = 0.75f;

// Our Window will be made of "gridRes" cells,
// where each cell is "cellSize" pixels big.
int WindowWidth = NX * CellPixels;
int WindowHeight = NY * CellPixels;

// Our simulation grids (Our State) will be one cell larger in each
// dimension to accomodate boundary conditions.
int GX = NX+2;
int GY = NY+2;

// The length of all of our (one-dimensional)
// arrays. We use 1d arrays rather than matrices
// mostly for efficiency reasons.
int GridArraySize = GX*GY;

// Whether to display velocities.
boolean DisplayVelocity = false;

PImage StateImage = createImage( GX, GY, RGB );


//-*****************************************************************************
//-*****************************************************************************
// SIMULATION STATE
//-*****************************************************************************
//-*****************************************************************************

// Our State Arrays
// 0  Uold 
// 1  Unew
// 2  Vold 
// 3  Vnew
// 4  DensityOld 
// 5  DensityNew
// 6  Input U
// 7  Input V
// 8  Input Density
// 9  Divergence
// 10 DiffusionTemp
// 11 NUM_ARRAYS
int NUM_ARRAYS = 12;
float[][] State = new float[NUM_ARRAYS][GridArraySize];
int GridPrevU = 0;
int GridU = 1;
int GridPrevV = 2;
int GridV = 3;
int GridPrevDensity = 4;
int GridDensity = 5;
int GridInputU = 6;
int GridInputV = 7;
int GridInputDensity = 8;
int GridTemp0 = 9;
int GridTemp1 = 10;
int GridTemp2 = 11;

float VstrokeAlpha = 0.5f;

// Index an element of a grid in the state array
public int IX( int i, int j )
{
    return ( i + GX*j ); 
}

//-*****************************************************************************
// Swap current arrays (velocity or density) with previous arrays.
public void SwapU() { int tmp = GridU; GridU = GridPrevU; GridPrevU = tmp; }
public void SwapV() { int tmp = GridV; GridV = GridPrevV; GridPrevV = tmp; }
public void SwapVelocity() { SwapU(); SwapV(); }
public void SwapDensity()
{ int tmp = GridDensity; GridDensity = GridPrevDensity; GridPrevDensity = tmp; }
public void SwapArrays() { SwapU(); SwapV(); SwapDensity(); }

//-*****************************************************************************
public void ZeroArray( int i_array )
{
    for ( int a = 0; a < GridArraySize; ++a )
    {
        State[i_array][a] = 0.0f;
    }
}

public void CopyArray( int i_src, int i_dst )
{
    for ( int a = 0; a < GridArraySize; ++a )
    {
        State[i_dst][a] = State[i_src][a];
    }
}

//-*****************************************************************************
//-*****************************************************************************
// INPUT
//-*****************************************************************************
//-*****************************************************************************

//-*****************************************************************************
// Add density based on mouse clicking
//-*****************************************************************************
public void GetInputSourceDensity()
{
    if ( mousePressed == true && mouseButton == LEFT ) 
    {
        for ( int j = 0; j < GY; ++j ) 
        {
            float cellMidPointY = CellPixels * ( 0.5f + ( float )j );

            for ( int i = 0; i < GX; ++i ) 
            {
                float cellMidPointX = CellPixels * ( 0.5f + ( float )i );

                float r = dist( mouseX, mouseY, cellMidPointX, cellMidPointY );
                float er2 = sq( 2.21f * r / EmissionRadius );
                float v = constrain( 2.0f * exp( -er2 ), 0.0f, 1.0f );

                State[GridInputDensity][IX(i,j)] = EmissionRate * v;
            } 
        }
    } 
    else 
    {
        ZeroArray( GridInputDensity );
    } 
}

//-*****************************************************************************
// Derive the velocity of the mouse
// when it's clicked; add the resulting
// velocities to the grid velocity.
//-*****************************************************************************
public void GetInputSourceVelocity()
{
    //if ( mousePressed == true && mouseButton == RIGHT ) 
    if ( mousePressed ) 
    {
        VstrokeAlpha = 0.5f;  

        float PixelVelX = ( mouseX - pmouseX ) / DT;
        float PixelVelY = ( mouseY - pmouseY ) / DT;
        float GridVelX = PixelVelX / ( float )CellPixels;
        float GridVelY = PixelVelY / ( float )CellPixels;
        float SimVelX = GridVelX * DXY;
        float SimVelY = GridVelY * DXY;

        for ( int j = 0; j < GY; ++j ) 
        {
            float cellMidPointY = CellPixels * ( 0.5f + ( float )j );

            for ( int i = 0; i < GX; ++i ) 
            {
                float cellMidPointX = CellPixels * ( 0.5f + ( float )i );

                float r = dist( mouseX, mouseY, cellMidPointX, cellMidPointY );
                float er2 = sq( 2.21f * r / EmissionRadius );
                float v = constrain( 2.0f * exp( -er2 ), 0.0f, 1.0f );
      
                State[GridInputU][IX(i,j)] = SimVelX * Vscale * v;
                State[GridInputV][IX(i,j)] = SimVelY * Vscale * v;
            } 
        }
    } 
    else 
    {
        ZeroArray( GridInputU );
        ZeroArray( GridInputV );
    }
}

//-*****************************************************************************
//-*****************************************************************************
// PHYSICS FUNCTIONS
//-*****************************************************************************
//-*****************************************************************************

//-*****************************************************************************
// Damping
public void DampArray( int io_grid, float i_damp )
{
    float mult = pow( constrain( 1.0f - i_damp, 0.0f, 1.0f ), DT );
    for ( int a = 0; a < GridArraySize; ++a )
    {
        State[io_grid][a] *= mult;    
    }
}

//-*****************************************************************************
// The boundary conditions are enforced on the I selector of the arrays.
// There are three types of boundary condition application - no negation,
// just copying at the boundary, then negating in the x-direction only,
// then negating in the y-direction only.
int BC_NoNegate = 0;
int BC_NegateX = 1;
int BC_NegateY = 2;

//-*****************************************************************************
public void EnforceBoundaryConditions( int io_grid, int i_bType )
{
    // Copy the bottom row from the one above it. If the boundary type
    // is '2', that means negate the values.
    // Do the same for the topmost row and the one beneath it.
    for ( int i = 1; i <= NX; ++i )
    {
        State[io_grid][IX( i, 0  )] = 
            ( i_bType == BC_NegateY ) ? 
            -State[io_grid][IX( i, 1 )] :
            State[io_grid][IX( i, 1 )];

        State[io_grid][IX( i, NY+1 )] = 
            ( i_bType == BC_NegateY ) ? 
            -State[io_grid][IX( i, NY )] :
            State[io_grid][IX( i, NY )];
    }

    // Copy the left col from the one to the right of it. If the boundary type
    // is '1', that means negate the values.
    // Do the same for the rightmost col and the one to the left of it.
    for ( int j = 1; j <= NY; ++j )
    {
        State[io_grid][IX( 0, j  )]   = 
            ( i_bType == BC_NegateX ) ? 
            -State[io_grid][IX( 1, j )] :
            State[io_grid][IX( 1, j )];
    
        State[io_grid][IX( NX+1, j )] = 
            ( i_bType == BC_NegateY ) ? 
            -State[io_grid][IX( NX, j )] :
            State[io_grid][IX( NX, j )];
    }

    // Get each corner by averaging the two boundary values adjacent.
    State[io_grid][IX(0,0)]     
        = 0.5f * ( State[io_grid][IX(1,0)] + State[io_grid][IX(0,1)] );
    State[io_grid][IX(0,NY+1)]   
        = 0.5f * ( State[io_grid][IX(1,NY+1)] + State[io_grid][IX(0,NY)] );
    State[io_grid][IX(NX+1,0)]   
        = 0.5f * ( State[io_grid][IX(NX,0)] + State[io_grid][IX(NX+1,1)] );
    State[io_grid][IX(NX+1,NY+1)] 
        = 0.5f * ( State[io_grid][IX(NX,NY+1)] + State[io_grid][IX(NX+1,NY)]);
}

//-*****************************************************************************
// Integrate External Forces (basically, in this case, just add the 
// input source velocity to the velocity)
public void IntegrateExternalVelocity()
{
    // We can work directly on final density.
    for ( int a = 0; a < GridArraySize; ++a )
    {
        State[GridU][a] += DT * State[GridInputU][a];
        State[GridV][a] += DT * State[GridInputV][a];
    }

    EnforceBoundaryConditions( GridU, BC_NegateX );
    EnforceBoundaryConditions( GridV, BC_NegateY );
}

//-*****************************************************************************
// Integrate External Densities (basically, in this case, just add the 
// input source density to the density)
public void IntegrateExternalDensity()
{
    if ( mousePressed && mouseButton == LEFT )
    {
  
    // We can work directly on final density.
    for ( int a = 0; a < GridArraySize; ++a )
    {
        State[GridDensity][a] += DT * State[GridInputDensity][a];
    }

    EnforceBoundaryConditions( GridDensity, BC_NoNegate );
    }
}

//-*****************************************************************************
// A simple equation for diffusion is the heat equation:
// dQ/dt = DiffusionRate * Laplacian( Q )
// which you can read more about here:
// http://en.wikipedia.org/wiki/Heat_equation
//
// As in the jacobi pressure solver below, we solve this equation by
// discretizing these differential operators and then solving for the 
// central value, assuming all other values are constant.
//
// ( Qcen - QcenOld ) / Dt = DiffusionRate * Laplacian( Q )
// ( Qcen - QcenOld ) - Dt * DiffusionRate * Laplacian( Q ) = 0
// k = Dt * DiffusionRate / DXY^2
// Qcen - QcenOld - k * (( Qdown + Qleft + Qright + Qup ) - 4.0Qcen) = 0
// (1 - 4*k)*Qcen - QcenOld - k*(Qdown + Qleft + Qright + Qup) = 0
// (1 - 4*k)*Qcen = QcenOld + k*(Qdown + Qleft + Qrigth + Qup)
// Qcen = (QcenOld + k*(Qdown + Qleft + Qright + Qup)) / ( 1 - 4*k )
// --------------------------------
// Diffuse (dissipate) density
// --------------------------------
public void Diffuse( int i_OldQ, int o_NewQ, float i_visc, int i_bType )
{
    float k = DT * i_visc * sq( DXY );
    //print( "k = " + k );

    // Create temporary handles to src and dst arrays, which
    // we will ping-pong. 
    int SRC = o_NewQ;
    int DST = i_OldQ;

    for ( int iters = 0; iters < 9; ++iters )
    {
        // Swap src and dst array pointers.
        int tmp = SRC; SRC = DST; DST = tmp;

        // Diffuse the SRC into DST.
        for ( int j=1; j<=NY; ++j ) 
        {
            for ( int i=1; i<=NX; ++i ) 
            {
                State[DST][IX(i,j)] = 
                ( State[SRC][IX(i,j)] + 
                  k * ( State[SRC][IX(i,j-1)] + 
                        State[SRC][IX(i-1,j)] + 
                        State[SRC][IX(i+1,j)] + 
                        State[SRC][IX(i,j+1)] ) )
                / ( 1.0f + 4.0f*k );
            }
        }

        // Enforce the boundary conditions.
        EnforceBoundaryConditions( DST, i_bType );
    }
}

//-*****************************************************************************
public void DiffuseDensity()
{
    SwapDensity();
    Diffuse( GridPrevDensity, GridDensity, D_viscosity, BC_NoNegate );
}

//-*****************************************************************************
public void DiffuseVelocity()
{
    SwapVelocity();
    Diffuse( GridPrevU, GridU, V_viscosity, BC_NegateX );
    Diffuse( GridPrevV, GridV, V_viscosity, BC_NegateY );
}


//-*****************************************************************************
public void SemiLagrangianAdvect( int i_OldQ, int o_NewQ,
                           int i_GridU, int i_GridV,
                           int i_bType )
{
    for ( int j=1; j<=NY; ++j ) 
    {
        float SimPosY = DXY * ( 0.5f + ( float )j );
        for ( int i=1; i<=NX; ++i ) 
        {
            float SimPosX = DXY * ( 0.5f + ( float )i );

            float SimVelX = State[i_GridU][IX(i,j)];
            float SimVelY = State[i_GridV][IX(i,j)];

            float SimSamplePosX = SimPosX - DT * SimVelX;
            float SimSamplePosY = SimPosY - DT * SimVelY;

            float GridSamplePosX = ( SimSamplePosX / DXY ) - 0.5f;
            float GridSamplePosY = ( SimSamplePosY / DXY ) - 0.5f;

            int MinI = ( int )floor( GridSamplePosX );
            float InterpU = GridSamplePosX - ( float )MinI;
            MinI = constrain( MinI, 0, GX-1 );

            int MinJ = ( int )floor( GridSamplePosY );
            float InterpV = GridSamplePosY - ( float )MinJ;
            MinJ = constrain( MinJ, 0, GY-1 );

            int MaxI = constrain( MinI+1, 0, GX-1 );
            int MaxJ = constrain( MinJ+1, 0, GY-1 );

            float Q00 = State[i_OldQ][IX(MinI,MinJ)];
            float Q10 = State[i_OldQ][IX(MaxI,MinJ)];
            float Q01 = State[i_OldQ][IX(MinI,MaxJ)];
            float Q11 = State[i_OldQ][IX(MaxI,MaxJ)];

            float Qdown = lerp( Q00, Q10, InterpU );
            float Qup = lerp( Q01, Q11, InterpU );

            State[o_NewQ][IX(i,j)] = lerp( Qdown, Qup, InterpV );
        }
    }
    EnforceBoundaryConditions( o_NewQ, i_bType );
}

//-*****************************************************************************
public void AdvectDensity()
{
    SwapDensity();
    SemiLagrangianAdvect( GridPrevDensity, GridDensity,
                          GridU, GridV, BC_NoNegate );
}

//-*****************************************************************************
public void AdvectVelocity()
{
    SwapVelocity();
    SemiLagrangianAdvect( GridPrevU, GridU,
                          GridPrevU, GridPrevV, BC_NegateX );
    SemiLagrangianAdvect( GridPrevV, GridV,
                          GridPrevU, GridPrevV, BC_NegateY );
}

//-*****************************************************************************
// Compute Divergence.
// 
// Compute the divergence. This is the amount of mass entering
// or exiting each cell. In an incompressible fluid, divergence is
// zero - and that's what we're solving for!
//
// It is equal to the partial derivative of the x-velocity in the x direction
// (dU/dx),
// plus the partial derivative of the y-velocity in the y direction
// (dV/dy).
// We're using a central differencing scheme for computing the derivatives 
// here.
//-*****************************************************************************
public void ComputeDivergence( int i_gridU, int i_gridV, int o_gridDiv )
{
    for ( int j = 1; j <= NY; ++j )
    {
        for ( int i = 1; i <= NX; ++i )
        {
            float twoDU = State[i_gridU][IX(i+1,j)] - State[i_gridU][IX(i-1,j)];
            float twoDV = State[i_gridV][IX(i,j+1)] - State[i_gridV][IX(i,j-1)];
            State[o_gridDiv][IX(i,j)] = 
                ( twoDU / (2.0f*DXY) ) + ( twoDV / (2.0f*DXY) );
        }
    }
    
    // Compute Divergence Boundary conditions.
    EnforceBoundaryConditions( o_gridDiv, BC_NoNegate );
}

//-*****************************************************************************
// We're using the Projection Method for solving an incompressible flow.
//
// Information about this method can be found here:
// http://en.wikipedia.org/wiki/Projection_method_(fluid_dynamics)
//
// But, essentially, what it means is that we're breaking the solving
// of the equation up into separate steps, roughly:
// Add External Forces
// Advect
// Compute Divergence
// Find Pressure Which Will Correct Divergence
// Adjust Velocity By Negative Gradient Of Pressure.
//
// This function implements the Find Pressure part, by iteratively
// solving for a pressure which satisifies (at every point in the grid)
// the relationship:  Laplacian( Pressure ) = Divergence.
// The Laplacian is a differential operator which is equal to the
// second partial derivative with respect to x plus the second partial
// derivative with respect to y.  This can be discretized in the following
// way:
//
// SecondDerivX( P, i, j ) = ( ( P[i+1,j] - P[i,j] ) / DXY ) -
//                             ( P[i,j] - P[i-1,j] ) / DXY ) ) / DXY
// SecondDerivY( P, i, j ) = ( ( P[i,j+1] - P[i,j] ) / DXY ) -
//                             ( P[i,j] - P[i,j-1] ) / DXY ) ) / DXY
// 
// Laplacian( P, i, j ) = SecondDerivX( P, i, j ) + SecondDerivY( P, i, j )
// 
// This simplifies to:
// (( P[i,j-1] + P[i-1,j] + P[i+1,j] + P[i,j+1] ) - 4 P[i,j] ) / DXY^2
// 
// The idea with jacobi iteration is to assume, at every i,j point, that
// all the other values are constant, and just solve for one's self.
// This needs to be done into a new destination array, otherwise you'll be
// writing over your data as you're computing it. There are simulation
// techniques such as red-black gauss-seidel which can compute the data
// in-place, but we'll just swap between an old and a new pressure.
//
// Given the above expression for the lapacian at i,j, and the relationship
// Laplacian( P ) = Divergence
// We set the above expression to Divergence[i,j], and then rearrange the
// equation so that we're solving for P[i,j]:
//
// (( Pdown + Pleft + Pright + Pup ) - 4 Pcen )/H^2 = Div
// ( Pdown + Pleft + Pright + Pup ) - 4 Pcen  = H^2 * Div
// -4 Pcen = ( H^2 * Div ) - ( Pdown + Pleft + Pright + Pup )
// 4 Pcen = ( Pdown + Pleft + Pright + Pup ) - ( H^2 * Div )
// Pcen = ( ( Pdown + Pleft + Pright + Pup ) - ( H^2 * Div ) ) / 4
//
// P[i,j] = -( DXY^2 * Divergence[i,j] + 
//          (( P[i,j-1] + P[i-1,j] + P[i+1,j] + P[i,j+1] )) ) / 4
// 
//-*****************************************************************************
public void ComputePressureViaJacobiIterations( int i_Div, int o_Pressure, int i_tmp )
{
    // Init array indices.
    int SRC = o_Pressure;
    int DST = i_tmp;

    // Init the DST pressure to zero. It will be swapped into the SRC
    // location in the loop below.
    ZeroArray( DST );
    
    // Iterate 20 times, improving the pressure current from the pressure
    // prev.
    for ( int iter = 0; iter < 10; ++iter )
    {
        // Swap the indices of the current & previous pressure arrays.
        int tmp = SRC; SRC = DST; DST = tmp;

        // Do a single jacobi iteration to compute the current pressure
        // from the previous pressure.
        for ( int j = 1; j <= NY; ++j )
        {
            for ( int i = 1; i <= NX; ++i )
            {
                State[DST][IX(i,j)] =
                ( ( State[SRC][IX(i,j-1)] +
                    State[SRC][IX(i-1,j)] +
                    State[SRC][IX(i+1,j)] +
                    State[SRC][IX(i,j+1)] ) -
                  ( DXY * DXY * State[i_Div][IX(i,j)] ) ) / 4.0f;
            }
        }

        // Okay we've solved for DST. Enforce boundary conditions on it,
        // without negating in any direction.
        EnforceBoundaryConditions( DST, BC_NoNegate );
    }
}

//-*****************************************************************************
// Apply Negative Gradient of Pressure to Velocity
public void ApplyNegativeGradientOfPressureToVelocity( int i_pressure,
                                                int o_velU,
                                                int o_velV )
{ 
    for ( int j = 1; j <= NY; ++j )
    {
        for ( int i = 1; i <= NX; ++i )
        {
            float twoDPx = State[i_pressure][IX(i+1,j)] - 
                           State[i_pressure][IX(i-1,j)];
            float twoDPy = State[i_pressure][IX(i,j+1)] - 
                           State[i_pressure][IX(i,j-1)];
        
            State[o_velU][IX(i,j)] -= twoDPx / (2.0f*DXY);
            State[o_velV][IX(i,j)] -= twoDPy / (2.0f*DXY);
        }
    }

    // And apply boundary conditions. The U velocities are negated horizonally,
    // and the V velocities are negated vertically. This makes the fluid
    // reflect off the boundaries.
    EnforceBoundaryConditions( o_velU, BC_NegateX );
    EnforceBoundaryConditions( o_velV, BC_NegateY );
}

//-*****************************************************************************
public void EnforceIncompressibility()
{
    int DIV = GridTemp0;
    int PRES = GridTemp1;
    int TMP = GridTemp2;
    ComputeDivergence( GridU, GridV, DIV );
    ComputePressureViaJacobiIterations( DIV, PRES, TMP );
    ApplyNegativeGradientOfPressureToVelocity( PRES, GridU, GridV );
}

//-*****************************************************************************
//-*****************************************************************************
// SIMULATION TIME STEP
//-*****************************************************************************
//-*****************************************************************************

//-*****************************************************************************
public void FluidTimeStep()
{
    // Get External Input
    GetInputSourceDensity();
    GetInputSourceVelocity();

    // Solve Velocities
    AdvectVelocity();
    DampArray( GridU, V_damp );
    DampArray( GridV, V_damp );
    DiffuseVelocity();
    IntegrateExternalVelocity();
    EnforceIncompressibility();
    //AdvectFluid();
    //EnforceIncompressibility();

    // Solve Densities
    AdvectDensity();
    DiffuseDensity();
    DampArray( GridDensity, D_damp );
    IntegrateExternalDensity();
    //DiffuseDensities();
    //IntegrateExternalDensities();
    //AdvectDensities();
}

//-*****************************************************************************
//-*****************************************************************************
// PROCESSING SETUP FUNCTION
//-*****************************************************************************
//-*****************************************************************************

//-*****************************************************************************
public void setup() 
{
    //noSmooth();

    // Set up normalized colors.
    colorMode( RGB, 1.0f );
    
    // The scale of our window
    size( WindowWidth, WindowHeight );
    
    // Init density to a noise function.
    //for ( int j = 0; j < GY; j++ )
    //{
    //    float fj = j;
    //    for ( int i = 0; i < GX; i++ )
    //    {
    //        float fi = i;
    //        State[GridDensity][IX(i,j)] = noise( fi / 16.0, fj / 16.0 );
    //    }
    //}

    // Zero out our state to start.
    ZeroArray( GridU );
    ZeroArray( GridV );
    ZeroArray( GridPrevU );
    ZeroArray( GridPrevV );
    ZeroArray( GridPrevDensity );
    ZeroArray( GridDensity );
}

//-*****************************************************************************
// DRAW SCALAR FIELD
//-*****************************************************************************
public void DrawScalarField( int i_field )
{
    float pixr, pixg, pixb;
    float d;
    StateImage.loadPixels();
    //for ( int wj = 0; wj < height; ++wj )
    for ( int wj = 0; wj < GY; ++wj )
    {
        //int j = constrain( 1+( int )( (( float )wj) / (( float )CellPixels) ), 1, NY ); 

        //for ( int wi = 0; wi < width; ++wi )
        for ( int wi = 0; wi < GX; ++wi )
        {
            //int i = constrain( 1+( int )( (( float )wi) / (( float )CellPixels) ), 1, NX );

            d = constrain( State[i_field][IX(wi,wj)], 0.0f, 1.0f );

            //colorMode(HSB, 1);
            //float h = map(d, 0, 1, 0.675, .55);
            //float s = map(d, 0, 1, 0, 1);
            //float b = map(d, 0, 1, 1, .25);
            pixr = 0.9f * ( 1.0f - d );
            pixg = 0.9f * ( 1.0f - (d*d) );
            pixb = 0.9f * ( 1.0f - (d*d*d) );

            StateImage.pixels[ wi + (wj*GX) ] = color( pixr, pixg, pixb );
        }
    }
    StateImage.updatePixels();
    image( StateImage, 0, 0, width, height );
}

//-*****************************************************************************
// DRAW VELOCITY FIELD
//-*****************************************************************************
public void DrawVelocityField( int i_fieldU, int i_fieldV )
{
    if ( VstrokeAlpha > 0.001f ) 
    {
        colorMode( HSB, 1 );
        for ( int j = 1; j <= NY; ++j ) 
        {
            for ( int i = 1; i <= NX; ++i ) 
            {
                float lineStartX = CellPixels * ( 0.5f + ( float )( i ) );
                float lineStartY = CellPixels * ( 0.5f + ( float )( j ) );

                float lineDX = 20.0f * State[i_fieldU][IX(i,j)]/Vscale;
                float lineDY = 20.0f * State[i_fieldV][IX(i,j)]/Vscale;
                float lineLen = sqrt( sq( lineDX ) + sq( lineDY ) );

                float vmag = map( lineLen, 0, 5, 0.1f, 1);


                float h = map(vmag, 0, 1, 0, .025f);
                float s = map(vmag, 0, 1, 1, .9f);
                float b = map(vmag, 0, 1, 0, 1);

                stroke(h, s, b, VstrokeAlpha);

                line( lineStartX, lineStartY, 
                      lineStartX + lineDX, lineStartY + lineDY );
            } 
        }
        colorMode( RGB, 1 );
        VstrokeAlpha *= 0.98f;
    }
}

//-*****************************************************************************
// Key release function
public void keyReleased()
{
    if ( key == 118 )
    {
        DisplayVelocity = !DisplayVelocity;
    }  
}

//-*****************************************************************************
//-*****************************************************************************
// PROCESSING DRAW FUNCTION
//-*****************************************************************************
//-*****************************************************************************

//-*****************************************************************************
public void draw()
{
    background( 0.5f );

    FluidTimeStep();

    DrawScalarField( GridDensity );
    if ( DisplayVelocity )
    {
        DrawVelocityField( GridU, GridV );
    }
}

  static public void main(String[] passedArgs) {
    String[] appletArgs = new String[] { "sketch_130511a" };
    if (passedArgs != null) {
      PApplet.main(concat(appletArgs, passedArgs));
    } else {
      PApplet.main(appletArgs);
    }
  }
}
