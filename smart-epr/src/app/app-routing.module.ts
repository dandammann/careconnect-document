import { NgModule } from '@angular/core';
import {RouterModule, Routes} from "@angular/router";
import {LoginComponent} from "./modules/login/login.component";
import {LogoutComponent} from "./modules/logout/logout.component";
import {AuthGuard} from "./service/auth-guard";
import {PingComponent} from "./modules/ping/ping.component";
import {EprComponent} from "./modules/epr/epr.component";
import {CallbackComponent} from "./modules/callback/callback.component";

const routes: Routes = [
  { path: '', component: LoginComponent },
  { path: 'login', component: LoginComponent },

  { path: 'logout', component: LogoutComponent },
  { path: 'ping', canActivate: [AuthGuard], component: PingComponent },
  { path: 'epr', canActivate: [AuthGuard], component: EprComponent },
  { path: 'logout', component: LogoutComponent },
  { path: 'callback', component: CallbackComponent },

];

@NgModule({
  imports: [ RouterModule.forRoot(routes) ],
  exports: [
    RouterModule
  ]
})



export class AppRoutingModule {



}
